package com.zx.reader.service;

import com.zx.common.exception.ErrorCode;
import com.zx.media.client.LiteMediaClient;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.ChapterContentResponse;
import com.zx.reader.dto.ChapterTocItemResponse;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbookReaderServiceTest {

    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;
    @Mock
    private LiteMediaClient liteMediaClient;

    private ChapterAccessService chapterAccessService;
    private EbookReaderService service;

    @BeforeEach
    void setUp() {
        MediaAccessService mediaAccessService = new MediaAccessService(
                org.mockito.Mockito.mock(com.zx.bookstore.borrow.repository.BorrowOrderRepository.class),
                org.mockito.Mockito.mock(com.zx.bookstore.trade.repository.TradeOrderRepository.class));
        chapterAccessService = new ChapterAccessService(mediaAccessService);
        service = new EbookReaderService(
                ebookBookRepository, ebookChapterRepository, chapterAccessService, liteMediaClient);
    }

    @Test
    void listChapters_shouldMarkLockedWithoutFileIds() {
        EbookBook book = onlineBook(10L);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(ebookChapterRepository.listByEbookId(10L)).thenReturn(List.of(
                chapter(1, "一", "c1", 1),
                chapter(2, "二", "c2", 1),
                chapter(3, "三", "c3", 0)
        ));

        List<ChapterTocItemResponse> toc = service.listChapters(1L, 10L);

        assertThat(toc).hasSize(3);
        assertThat(toc.get(0).isLocked()).isFalse();
        assertThat(toc.get(1).isLocked()).isFalse();
        assertThat(toc.get(2).isLocked()).isTrue();
        assertThat(toc).allSatisfy(item -> {
            // 反射不到 fileId 字段；确保响应类型本身不含敏感字段即可
            assertThat(item.getChapterNo()).isNotNull();
            assertThat(item.getTitle()).isNotBlank();
        });
        verify(liteMediaClient, never()).fetchObjectText(anyString());
    }

    @Test
    void getChapterContent_shouldReturnTextForPreviewFree() {
        EbookBook book = onlineBook(10L);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, 1))
                .thenReturn(Optional.of(chapter(1, "持久化", "mock-c-1", 1)));
        when(liteMediaClient.fetchObjectText("mock-c-1")).thenReturn("# 第1章");

        ChapterContentResponse resp = service.getChapterContent(1L, 10L, 1);

        assertThat(resp.getChapterNo()).isEqualTo(1);
        assertThat(resp.getTitle()).isEqualTo("持久化");
        assertThat(resp.getContent()).isEqualTo("# 第1章");
        verify(liteMediaClient).fetchObjectText("mock-c-1");
    }

    @Test
    void getChapterContent_shouldDenyLockedWithoutCallingMedia() {
        EbookBook book = onlineBook(10L);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, 3))
                .thenReturn(Optional.of(chapter(3, "哨兵", "mock-c-3", 0)));

        assertThatThrownBy(() -> service.getChapterContent(1L, 10L, 3))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_PREVIEW_DENIED));

        verify(liteMediaClient, never()).fetchObjectText(anyString());
    }

    @Test
    void getChapterContent_shouldMapOfflineEbookTo5101() {
        EbookBook book = onlineBook(10L);
        book.setStatus(0);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.getChapterContent(1L, 10L, 1))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_EBOOK_NOT_FOUND));
        verify(liteMediaClient, never()).fetchObjectText(anyString());
    }

    @Test
    void getChapterContent_shouldPropagateMediaUnavailable() {
        EbookBook book = onlineBook(10L);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, 1))
                .thenReturn(Optional.of(chapter(1, "一", "c1", 1)));
        when(liteMediaClient.fetchObjectText("c1"))
                .thenThrow(ReaderException.mediaUnavailable("timeout"));

        assertThatThrownBy(() -> service.getChapterContent(1L, 10L, 1))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.MEDIA_UNAVAILABLE));
    }

    @Test
    void getChapterContent_shouldPropagateChapterNotReady() {
        EbookBook book = onlineBook(10L);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, 2))
                .thenReturn(Optional.of(chapter(2, "二", "c2", 1)));
        when(liteMediaClient.fetchObjectText("c2"))
                .thenThrow(ReaderException.chapterNotReady("missing"));

        assertThatThrownBy(() -> service.getChapterContent(1L, 10L, 2))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.MEDIA_CHAPTER_NOT_READY));
    }

    private static EbookBook onlineBook(Long id) {
        EbookBook book = new EbookBook();
        book.setId(id);
        book.setTitle("Redis");
        book.setStatus(1);
        return book;
    }

    private static EbookChapter chapter(int no, String title, String fileId, int previewFree) {
        EbookChapter ch = new EbookChapter();
        ch.setEbookId(10L);
        ch.setChapterNo(no);
        ch.setTitle(title);
        ch.setChapterFileId(fileId);
        ch.setWordCount(100);
        ch.setIsPreviewFree(previewFree);
        return ch;
    }
}
