package com.zx.reader.agent;

import com.zx.common.exception.ErrorCode;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.ChapterContentResponse;
import com.zx.reader.dto.CreateNoteRequest;
import com.zx.reader.dto.NoteResponse;
import com.zx.reader.entity.UserNote;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.UserNoteRepository;
import com.zx.reader.service.EbookReaderService;
import com.zx.reader.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Study Agent Tools：鉴权与 B3 一致；改写/合并另存，不覆盖原文。
 */
@Slf4j
@Component
@ConditionalOnBean(StudyTextGenerator.class)
@RequiredArgsConstructor
public class StudyAgentTools {

    private final EbookReaderService ebookReaderService;
    private final NoteService noteService;
    private final UserNoteRepository userNoteRepository;
    private final EbookChapterRepository ebookChapterRepository;
    private final StudySummaryCacheService summaryCacheService;
    private final StudyTextGenerator textGenerator;

    @Tool(name = "getChapterContent",
            description = "获取指定电子书某一章正文（Markdown）。必须传入 ebookId 与 chapterNo。若无权限或章节锁定，返回 ok=false，禁止编造正文。")
    public Map<String, Object> getChapterContent(
            @ToolParam(description = "电子书 ID") Long ebookId,
            @ToolParam(description = "章序号，从 1 开始") Integer chapterNo
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        Long eid = ebookId != null ? ebookId : StudyAgentContext.ebookId();
        if (eid == null || chapterNo == null) {
            return err("ebookId 与 chapterNo 必填");
        }
        try {
            ChapterContentResponse content = ebookReaderService.getChapterContent(userId, eid, chapterNo);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("ebookId", eid);
            ok.put("chapterNo", content.getChapterNo());
            ok.put("title", content.getTitle());
            ok.put("content", content.getContent());
            return ok;
        } catch (ReaderException ex) {
            if (ex.getCode() == ErrorCode.READER_PREVIEW_DENIED) {
                return err("本章需借阅或购买后阅读，无权限获取正文，请勿编造内容");
            }
            return err(ex.getMessage());
        } catch (Exception ex) {
            log.warn("getChapterContent failed ebookId={} chapterNo={}", eid, chapterNo, ex);
            return err("读取章节失败");
        }
    }

    @Tool(name = "getMyNotes",
            description = "查询当前用户的笔记列表。可按 ebookId 过滤；不传则用会话上下文中的 ebookId（若有）。")
    public Map<String, Object> getMyNotes(
            @ToolParam(required = false, description = "电子书 ID，可选") Long ebookId
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        Long eid = ebookId != null ? ebookId : StudyAgentContext.ebookId();
        List<NoteResponse> notes = noteService.list(userId, eid);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("total", notes.size());
        ok.put("notes", notes.stream().map(StudyAgentTools::slimNote).toList());
        return ok;
    }

    @Tool(name = "saveNote",
            description = "为当前用户保存一条手动或划线笔记。sourceType 仅 MANUAL 或 HIGHLIGHT。")
    public Map<String, Object> saveNote(
            @ToolParam(required = false, description = "电子书 ID") Long ebookId,
            @ToolParam(required = false, description = "章节 ID（库内主键，可选）") Long chapterId,
            @ToolParam(description = "笔记正文") String content,
            @ToolParam(required = false, description = "标题") String title,
            @ToolParam(required = false, description = "划线摘录") String quoteText,
            @ToolParam(required = false, description = "MANUAL 或 HIGHLIGHT") String sourceType
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        try {
            CreateNoteRequest req = new CreateNoteRequest();
            req.setEbookId(ebookId != null ? ebookId : StudyAgentContext.ebookId());
            req.setChapterId(chapterId);
            req.setContent(content);
            req.setTitle(title);
            req.setQuoteText(quoteText);
            req.setSourceType(sourceType);
            NoteResponse saved = noteService.create(userId, req);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("note", slimNote(saved));
            return ok;
        } catch (ReaderException | IllegalArgumentException ex) {
            return err(ex.getMessage());
        }
    }

    @Tool(name = "summarizeChapter",
            description = "总结指定章节并另存为 AI_SUMMARY 笔记。优先使用总结缓存。无权限时不得编造。")
    public Map<String, Object> summarizeChapter(
            @ToolParam(description = "电子书 ID") Long ebookId,
            @ToolParam(description = "章序号") Integer chapterNo
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        Long eid = ebookId != null ? ebookId : StudyAgentContext.ebookId();
        if (eid == null || chapterNo == null) {
            return err("ebookId 与 chapterNo 必填");
        }
        try {
            ChapterContentResponse chapter = ebookReaderService.getChapterContent(userId, eid, chapterNo);
            String summary = summaryCacheService.get(eid, chapterNo).orElse(null);
            boolean cacheHit = summary != null;
            if (!cacheHit) {
                summary = textGenerator.generate(
                        "你是学习助手。请用中文总结章节要点，分条列出，不要编造原文没有的内容。",
                        "标题：" + chapter.getTitle() + "\n\n正文：\n" + chapter.getContent());
                summaryCacheService.put(eid, chapterNo, summary);
            }
            Long chapterId = resolveChapterId(eid, chapterNo);
            NoteResponse note = noteService.saveAiNote(
                    userId, eid, chapterId, "AI_SUMMARY",
                    "第" + chapterNo + "章总结", summary);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("cacheHit", cacheHit);
            ok.put("summary", summary);
            ok.put("note", slimNote(note));
            return ok;
        } catch (ReaderException ex) {
            if (ex.getCode() == ErrorCode.READER_PREVIEW_DENIED) {
                return err("本章无阅读权限，无法总结，请勿编造");
            }
            return err(ex.getMessage());
        } catch (Exception ex) {
            log.warn("summarizeChapter failed", ex);
            return err("总结失败：" + ex.getMessage());
        }
    }

    @Tool(name = "rewriteNote",
            description = "改写指定笔记并另存为 AI_REWRITE，不覆盖原笔记。可说明改写要求（如改短、口语化）。")
    public Map<String, Object> rewriteNote(
            @ToolParam(description = "原笔记 ID") Long noteId,
            @ToolParam(required = false, description = "改写要求，默认改得更简洁") String instruction
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        if (noteId == null) {
            return err("noteId 必填");
        }
        try {
            NoteResponse original = noteService.get(userId, noteId);
            String how = StringUtils.hasText(instruction) ? instruction.trim() : "改得更简洁清晰";
            String rewritten = textGenerator.generate(
                    "你是学习助手。按用户要求改写笔记，保留关键信息，使用中文，不要编造原文没有的事实。",
                    "改写要求：" + how + "\n\n原笔记标题：" + nullToEmpty(original.getTitle())
                            + "\n\n原笔记正文：\n" + original.getContent());
            NoteResponse saved = noteService.saveAiNote(
                    userId, original.getEbookId(), original.getChapterId(),
                    "AI_REWRITE",
                    "改写：" + nullToEmpty(original.getTitle()),
                    rewritten);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("originalNoteId", noteId);
            ok.put("note", slimNote(saved));
            return ok;
        } catch (ReaderException | IllegalArgumentException ex) {
            return err(ex.getMessage());
        } catch (Exception ex) {
            log.warn("rewriteNote failed noteId={}", noteId, ex);
            return err("改写失败");
        }
    }

    @Tool(name = "mergeNotes",
            description = "合并多条笔记为一条 AI_MERGE 新笔记，不删除原文。传入 noteIds；也可不传 noteIds 而传 ebookId 合并该书全部笔记。")
    public Map<String, Object> mergeNotes(
            @ToolParam(required = false, description = "要合并的笔记 ID 列表") List<Long> noteIds,
            @ToolParam(required = false, description = "若未传 noteIds，则合并该电子书下当前用户全部笔记") Long ebookId
    ) {
        Long userId = StudyAgentContext.userId();
        if (userId == null) {
            return err("未登录");
        }
        try {
            List<UserNote> notes = new ArrayList<>();
            if (noteIds != null && !noteIds.isEmpty()) {
                for (UserNote n : userNoteRepository.listByIds(noteIds)) {
                    if (userId.equals(n.getUserId())) {
                        notes.add(n);
                    }
                }
            } else {
                Long eid = ebookId != null ? ebookId : StudyAgentContext.ebookId();
                if (eid == null) {
                    return err("请提供 noteIds 或 ebookId");
                }
                notes.addAll(userNoteRepository.listByUserAndEbook(userId, eid));
            }
            if (notes.size() < 2) {
                return err("至少需要 2 条笔记才能合并");
            }
            StringBuilder material = new StringBuilder();
            for (int i = 0; i < notes.size(); i++) {
                UserNote n = notes.get(i);
                material.append("【笔记").append(i + 1).append("】")
                        .append(nullToEmpty(n.getTitle())).append('\n')
                        .append(n.getContent()).append("\n\n");
            }
            String merged = textGenerator.generate(
                    "你是学习助手。将多条笔记合并为一篇结构清晰的中文笔记，去重并保留要点，不要编造。",
                    material.toString());
            UserNote first = notes.getFirst();
            NoteResponse saved = noteService.saveAiNote(
                    userId, first.getEbookId(), first.getChapterId(),
                    "AI_MERGE", "合并笔记", merged);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("mergedFrom", notes.stream().map(UserNote::getId).toList());
            ok.put("note", slimNote(saved));
            return ok;
        } catch (Exception ex) {
            log.warn("mergeNotes failed", ex);
            return err("合并失败：" + ex.getMessage());
        }
    }

    private Long resolveChapterId(Long ebookId, int chapterNo) {
        return ebookChapterRepository.findByEbookIdAndChapterNo(ebookId, chapterNo)
                .map(ch -> ch.getId())
                .orElse(null);
    }

    private static Map<String, Object> slimNote(NoteResponse n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("ebookId", n.getEbookId());
        m.put("chapterId", n.getChapterId());
        m.put("sourceType", n.getSourceType());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        return m;
    }

    private static Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("message", message);
        return m;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
