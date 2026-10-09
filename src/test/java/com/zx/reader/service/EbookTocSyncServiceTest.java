package com.zx.reader.service;

import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.dto.ChapterInfo;
import com.zx.media.client.dto.ChaptersResult;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbookTocSyncServiceTest {

    @Mock
    private LiteMediaClient liteMediaClient;
    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;

    private ReaderProperties readerProperties;
    private EbookTocSyncService service;

    @BeforeEach
    void setUp() {
        readerProperties = new ReaderProperties();
        readerProperties.getPreview().setDefaultChapters(2);
        service = new EbookTocSyncService(
                liteMediaClient, ebookBookRepository, ebookChapterRepository, readerProperties);
    }

    @Test
    void syncBySourceFileId_shouldReplaceChaptersAndMarkPreview() {
        EbookBook book = new EbookBook();
        book.setId(10L);
        book.setSourceFileId("doc-1");
        book.setBookId(8L);
        book.setPreviewChapters(2);
        when(ebookBookRepository.findBySourceFileId("doc-1")).thenReturn(Optional.of(book));
        when(liteMediaClient.listChapters("doc-1")).thenReturn(new ChaptersResult("doc-1", List.of(
                new ChapterInfo(1, "一", "c1", 100, "CHAPTER"),
                new ChapterInfo(2, "二", "c2", 200, "CHAPTER"),
                new ChapterInfo(3, "三", "c3", 300, "CHAPTER")
        )));
        when(ebookChapterRepository.insertAll(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
        when(ebookBookRepository.save(any(EbookBook.class))).thenAnswer(inv -> inv.getArgument(0));

        int n = service.syncBySourceFileId("doc-1");
        assertThat(n).isEqualTo(3);

        verify(ebookChapterRepository).deleteByEbookId(10L);
        verify(ebookChapterRepository, never()).save(any());
        ArgumentCaptor<List<EbookChapter>> captor = ArgumentCaptor.forClass(List.class);
        verify(ebookChapterRepository).insertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue().get(0).getIsPreviewFree()).isEqualTo(1);
        assertThat(captor.getValue().get(1).getIsPreviewFree()).isEqualTo(1);
        assertThat(captor.getValue().get(2).getIsPreviewFree()).isEqualTo(0);
        assertThat(book.getTotalChapters()).isEqualTo(3);
        assertThat(book.getWordCount()).isEqualTo(600L);
    }

    @Test
    void syncBySourceFileId_shouldReturnMinusOneWhenNoEbook() {
        when(ebookBookRepository.findBySourceFileId("orphan")).thenReturn(Optional.empty());
        assertThat(service.syncBySourceFileId("orphan")).isEqualTo(-1);
        verify(liteMediaClient, never()).listChapters(any());
    }

    @Test
    void syncBySourceFileId_shouldFailWhenChaptersEmpty() {
        EbookBook book = new EbookBook();
        book.setId(1L);
        book.setSourceFileId("doc-empty");
        book.setBookId(8L);
        when(ebookBookRepository.findBySourceFileId("doc-empty")).thenReturn(Optional.of(book));
        when(liteMediaClient.listChapters("doc-empty"))
                .thenReturn(new ChaptersResult("doc-empty", List.of()));

        assertThatThrownBy(() -> service.syncBySourceFileId("doc-empty"))
                .isInstanceOf(ReaderException.class);
        verify(ebookChapterRepository, never()).deleteByEbookId(any());
    }

    @Test
    void syncBySourceFileId_shouldAllowWhenBookIdNull() {
        EbookBook book = new EbookBook();
        book.setId(4L);
        book.setSourceFileId("doc-unbound");
        book.setPreviewChapters(2);
        when(ebookBookRepository.findBySourceFileId("doc-unbound")).thenReturn(Optional.of(book));
        when(liteMediaClient.listChapters("doc-unbound")).thenReturn(new ChaptersResult("doc-unbound", List.of(
                new ChapterInfo(1, "一", "c1", 10, "CHAPTER")
        )));
        when(ebookChapterRepository.insertAll(any())).thenReturn(1);
        when(ebookBookRepository.save(any(EbookBook.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.syncBySourceFileId("doc-unbound")).isEqualTo(1);
        verify(ebookChapterRepository).deleteByEbookId(4L);
        verify(ebookChapterRepository).insertAll(any());
    }
}
