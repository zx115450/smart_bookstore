package com.zx.reader.service;

import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.CreateNoteRequest;
import com.zx.reader.dto.NoteResponse;
import com.zx.reader.dto.UpdateNoteRequest;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.entity.UserNote;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.UserNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 用户笔记 CRUD；锁定章超长划线拒绝（防泄文）。
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private static final Set<String> ALLOWED_CREATE_TYPES = Set.of("MANUAL", "HIGHLIGHT");
    private static final Set<String> AI_SOURCE_TYPES = Set.of("AI_SUMMARY", "AI_REWRITE", "AI_MERGE");

    private final UserNoteRepository userNoteRepository;
    private final EbookBookRepository ebookBookRepository;
    private final EbookChapterRepository ebookChapterRepository;
    private final ChapterAccessService chapterAccessService;
    private final ReaderProperties readerProperties;

    public NoteResponse create(Long userId, CreateNoteRequest req) {
        return create(userId, req, null);
    }

    public NoteResponse create(Long userId, CreateNoteRequest req, Collection<String> roles) {
        if (req == null || !StringUtils.hasText(req.getContent())) {
            throw new IllegalArgumentException("content 必填");
        }

        EbookBook book = null;
        if (req.getEbookId() != null) {
            book = ebookBookRepository.findById(req.getEbookId())
                    .orElseThrow(ReaderException::ebookNotFound);
        }

        EbookChapter chapter = null;
        if (req.getChapterId() != null) {
            chapter = ebookChapterRepository.findById(req.getChapterId())
                    .orElseThrow(ReaderException::ebookNotFound);
            if (book != null && !book.getId().equals(chapter.getEbookId())) {
                throw ReaderException.ebookNotFound();
            }
            if (book == null) {
                book = ebookBookRepository.findById(chapter.getEbookId())
                        .orElseThrow(ReaderException::ebookNotFound);
            }
        }

        String content = req.getContent().trim();
        String quote = trimToNull(req.getQuoteText());
        guardLockedText(userId, book, chapter, roles, content, contentLimit(), ReaderException.noteContentDenied());
        guardLockedText(userId, book, chapter, roles, quote, quoteLimit(), ReaderException.noteQuoteDenied());

        String sourceType = resolveCreateSourceType(req.getSourceType(), quote);

        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setEbookId(book == null ? req.getEbookId() : book.getId());
        note.setChapterId(chapter == null ? req.getChapterId() : chapter.getId());
        note.setBookId(req.getBookId() != null ? req.getBookId() : (book == null ? null : book.getBookId()));
        note.setFileId(trimToNull(req.getFileId()));
        note.setSourceType(sourceType);
        note.setTitle(trimToNull(req.getTitle()));
        note.setContent(content);
        note.setQuoteText(quote);
        note.setTags(trimToNull(req.getTags()));
        return toResponse(userNoteRepository.save(note));
    }

    public List<NoteResponse> list(Long userId, Long ebookId) {
        return userNoteRepository.listByUserAndEbook(userId, ebookId).stream()
                .map(this::toResponse)
                .toList();
    }

    public NoteResponse get(Long userId, Long noteId) {
        return toResponse(requireOwned(userId, noteId));
    }

    public NoteResponse update(Long userId, Long noteId, UpdateNoteRequest req) {
        return update(userId, noteId, req, null);
    }

    public NoteResponse update(Long userId, Long noteId, UpdateNoteRequest req, Collection<String> roles) {
        if (req == null) {
            throw new IllegalArgumentException("body 必填");
        }
        UserNote note = requireOwned(userId, noteId);

        EbookBook book = note.getEbookId() == null ? null
                : ebookBookRepository.findById(note.getEbookId()).orElse(null);
        EbookChapter chapter = note.getChapterId() == null ? null
                : ebookChapterRepository.findById(note.getChapterId()).orElse(null);

        if (req.getContent() != null) {
            if (!StringUtils.hasText(req.getContent())) {
                throw new IllegalArgumentException("content 不能为空");
            }
            String content = req.getContent().trim();
            guardLockedText(userId, book, chapter, roles, content, contentLimit(), ReaderException.noteContentDenied());
            note.setContent(content);
        }
        if (req.getTitle() != null) {
            note.setTitle(trimToNull(req.getTitle()));
        }
        if (req.getTags() != null) {
            note.setTags(trimToNull(req.getTags()));
        }
        if (req.getQuoteText() != null) {
            String quote = trimToNull(req.getQuoteText());
            guardLockedText(userId, book, chapter, roles, quote, quoteLimit(), ReaderException.noteQuoteDenied());
            note.setQuoteText(quote);
            if (quote != null && "MANUAL".equals(note.getSourceType())) {
                note.setSourceType("HIGHLIGHT");
            }
        }
        return toResponse(userNoteRepository.save(note));
    }

    public void delete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        userNoteRepository.deleteById(noteId);
    }

    /**
     * Study Agent 写入 AI 笔记（另存，不覆盖原文）。
     */
    public NoteResponse saveAiNote(Long userId, Long ebookId, Long chapterId,
                                   String sourceType, String title, String content) {
        return saveAiNote(userId, ebookId, chapterId, sourceType, title, content, null);
    }

    public NoteResponse saveAiNote(Long userId, Long ebookId, Long chapterId,
                                   String sourceType, String title, String content,
                                   Collection<String> roles) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content 必填");
        }
        String type = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if (!AI_SOURCE_TYPES.contains(type)) {
            throw new IllegalArgumentException("非法 AI sourceType: " + sourceType);
        }
        EbookBook book = null;
        if (ebookId != null) {
            book = ebookBookRepository.findById(ebookId).orElseThrow(ReaderException::ebookNotFound);
        }
        EbookChapter chapter = null;
        if (chapterId != null) {
            chapter = ebookChapterRepository.findById(chapterId)
                    .orElseThrow(ReaderException::ebookNotFound);
            if (ebookId != null && !ebookId.equals(chapter.getEbookId())) {
                throw ReaderException.ebookNotFound();
            }
            if (ebookId == null) {
                ebookId = chapter.getEbookId();
                book = ebookBookRepository.findById(ebookId).orElseThrow(ReaderException::ebookNotFound);
            }
        }
        String stored = content.trim();
        guardLockedText(userId, book, chapter, roles, stored, contentLimit(), ReaderException.noteContentDenied());
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setEbookId(ebookId);
        note.setChapterId(chapterId);
        note.setSourceType(type);
        note.setTitle(trimToNull(title));
        note.setContent(stored);
        return toResponse(userNoteRepository.save(note));
    }

    /**
     * 锁定章分别限制划线与笔记正文，避免把章节原文整段存进笔记。
     */
    private void guardLockedText(Long userId, EbookBook book, EbookChapter chapter,
                                 Collection<String> roles, String text, int max, ReaderException denied) {
        if (text == null || chapter == null || book == null) {
            return;
        }
        if (text.length() <= Math.max(0, max)) {
            return;
        }
        if (!chapterAccessService.canReadChapter(userId, book, chapter, roles)) {
            throw denied;
        }
    }

    private int quoteLimit() {
        return readerProperties.getNotes().getMaxQuoteOnLocked();
    }

    private int contentLimit() {
        return readerProperties.getNotes().getMaxContentOnLocked();
    }

    private UserNote requireOwned(Long userId, Long noteId) {
        UserNote note = userNoteRepository.findById(noteId)
                .orElseThrow(ReaderException::noteNotFound);
        if (!userId.equals(note.getUserId())) {
            throw ReaderException.noteForbidden();
        }
        return note;
    }

    private static String resolveCreateSourceType(String requested, String quote) {
        if (StringUtils.hasText(requested)) {
            String t = requested.trim().toUpperCase();
            if (!ALLOWED_CREATE_TYPES.contains(t)) {
                throw new IllegalArgumentException("sourceType 仅支持 MANUAL / HIGHLIGHT");
            }
            return t;
        }
        return quote != null ? "HIGHLIGHT" : "MANUAL";
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private NoteResponse toResponse(UserNote note) {
        NoteResponse resp = new NoteResponse();
        resp.setId(note.getId());
        resp.setUserId(note.getUserId());
        resp.setEbookId(note.getEbookId());
        resp.setChapterId(note.getChapterId());
        resp.setBookId(note.getBookId());
        resp.setFileId(note.getFileId());
        resp.setSourceType(note.getSourceType());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setQuoteText(note.getQuoteText());
        resp.setTags(note.getTags());
        resp.setCreatedAt(note.getCreatedAt());
        resp.setUpdatedAt(note.getUpdatedAt());
        return resp;
    }
}
