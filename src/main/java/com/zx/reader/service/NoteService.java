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

        String quote = trimToNull(req.getQuoteText());
        guardQuoteAgainstLeak(userId, book, chapter, quote);

        String sourceType = resolveCreateSourceType(req.getSourceType(), quote);

        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setEbookId(book == null ? req.getEbookId() : book.getId());
        note.setChapterId(chapter == null ? req.getChapterId() : chapter.getId());
        note.setBookId(req.getBookId() != null ? req.getBookId() : (book == null ? null : book.getBookId()));
        note.setFileId(trimToNull(req.getFileId()));
        note.setSourceType(sourceType);
        note.setTitle(trimToNull(req.getTitle()));
        note.setContent(req.getContent().trim());
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
        if (req == null) {
            throw new IllegalArgumentException("body 必填");
        }
        UserNote note = requireOwned(userId, noteId);

        if (req.getContent() != null) {
            if (!StringUtils.hasText(req.getContent())) {
                throw new IllegalArgumentException("content 不能为空");
            }
            note.setContent(req.getContent().trim());
        }
        if (req.getTitle() != null) {
            note.setTitle(trimToNull(req.getTitle()));
        }
        if (req.getTags() != null) {
            note.setTags(trimToNull(req.getTags()));
        }
        if (req.getQuoteText() != null) {
            String quote = trimToNull(req.getQuoteText());
            EbookBook book = note.getEbookId() == null ? null
                    : ebookBookRepository.findById(note.getEbookId()).orElse(null);
            EbookChapter chapter = note.getChapterId() == null ? null
                    : ebookChapterRepository.findById(note.getChapterId()).orElse(null);
            guardQuoteAgainstLeak(userId, book, chapter, quote);
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
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content 必填");
        }
        String type = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if (!AI_SOURCE_TYPES.contains(type)) {
            throw new IllegalArgumentException("非法 AI sourceType: " + sourceType);
        }
        if (ebookId != null) {
            ebookBookRepository.findById(ebookId).orElseThrow(ReaderException::ebookNotFound);
        }
        if (chapterId != null) {
            EbookChapter chapter = ebookChapterRepository.findById(chapterId)
                    .orElseThrow(ReaderException::ebookNotFound);
            if (ebookId != null && !ebookId.equals(chapter.getEbookId())) {
                throw ReaderException.ebookNotFound();
            }
            if (ebookId == null) {
                ebookId = chapter.getEbookId();
            }
        }
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setEbookId(ebookId);
        note.setChapterId(chapterId);
        note.setSourceType(type);
        note.setTitle(trimToNull(title));
        note.setContent(content.trim());
        return toResponse(userNoteRepository.save(note));
    }

    private void guardQuoteAgainstLeak(Long userId, EbookBook book, EbookChapter chapter, String quote) {
        if (quote == null || chapter == null || book == null) {
            return;
        }
        int max = Math.max(0, readerProperties.getNotes().getMaxQuoteOnLocked());
        if (quote.length() <= max) {
            return;
        }
        if (!chapterAccessService.canReadChapter(userId, book, chapter)) {
            throw ReaderException.noteQuoteDenied();
        }
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
