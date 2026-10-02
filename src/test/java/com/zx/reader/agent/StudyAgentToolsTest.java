package com.zx.reader.agent;

import com.zx.common.exception.ErrorCode;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.ChapterContentResponse;
import com.zx.reader.dto.NoteResponse;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.UserNoteRepository;
import com.zx.reader.service.EbookReaderService;
import com.zx.reader.service.NoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyAgentToolsTest {

    @Mock
    private EbookReaderService ebookReaderService;
    @Mock
    private NoteService noteService;
    @Mock
    private UserNoteRepository userNoteRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;
    @Mock
    private StudySummaryCacheService summaryCacheService;
    @Mock
    private StudyTextGenerator textGenerator;

    private StudyAgentTools tools;

    @BeforeEach
    void setUp() {
        tools = new StudyAgentTools(
                ebookReaderService, noteService, userNoteRepository,
                ebookChapterRepository, summaryCacheService, textGenerator);
        StudyAgentContext.set(1L, 10L);
    }

    @AfterEach
    void tearDown() {
        StudyAgentContext.clear();
    }

    @Test
    void getChapterContent_denied_shouldNotFabricate() {
        when(ebookReaderService.getChapterContent(1L, 10L, 3))
                .thenThrow(ReaderException.previewDenied());

        Map<String, Object> result = tools.getChapterContent(10L, 3);

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(result.get("message"))).contains("无权限");
    }

    @Test
    void summarizeChapter_cacheHit_shouldSkipModel() {
        ChapterContentResponse chapter = new ChapterContentResponse();
        chapter.setChapterNo(2);
        chapter.setTitle("主从");
        chapter.setContent("正文");
        when(ebookReaderService.getChapterContent(1L, 10L, 2)).thenReturn(chapter);
        when(summaryCacheService.get(10L, 2)).thenReturn(Optional.of("缓存总结"));
        when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, 2)).thenReturn(Optional.empty());
        when(noteService.saveAiNote(eq(1L), eq(10L), any(), eq("AI_SUMMARY"), anyString(), eq("缓存总结")))
                .thenAnswer(inv -> {
                    NoteResponse n = new NoteResponse();
                    n.setId(99L);
                    n.setSourceType("AI_SUMMARY");
                    n.setContent(inv.getArgument(5));
                    return n;
                });

        Map<String, Object> result = tools.summarizeChapter(10L, 2);

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("cacheHit")).isEqualTo(true);
        verify(textGenerator, never()).generate(anyString(), anyString());
        verify(summaryCacheService, never()).put(anyLong(), anyInt(), anyString());
    }

    @Test
    void rewriteNote_shouldSaveAiRewrite() {
        NoteResponse original = new NoteResponse();
        original.setId(5L);
        original.setEbookId(10L);
        original.setChapterId(101L);
        original.setTitle("原");
        original.setContent("很长的原文");
        when(noteService.get(1L, 5L)).thenReturn(original);
        when(textGenerator.generate(anyString(), anyString())).thenReturn("短文");
        when(noteService.saveAiNote(eq(1L), eq(10L), eq(101L), eq("AI_REWRITE"), anyString(), eq("短文")))
                .thenAnswer(inv -> {
                    NoteResponse n = new NoteResponse();
                    n.setId(6L);
                    n.setSourceType("AI_REWRITE");
                    n.setContent("短文");
                    return n;
                });

        Map<String, Object> result = tools.rewriteNote(5L, "改短");

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("originalNoteId")).isEqualTo(5L);
        @SuppressWarnings("unchecked")
        Map<String, Object> note = (Map<String, Object>) result.get("note");
        assertThat(note.get("sourceType")).isEqualTo("AI_REWRITE");
        assertThat(note.get("id")).isEqualTo(6L);
    }
}
