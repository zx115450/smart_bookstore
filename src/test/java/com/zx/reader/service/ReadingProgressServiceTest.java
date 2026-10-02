package com.zx.reader.service;

import com.zx.common.exception.ErrorCode;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.ReadingProgressResponse;
import com.zx.reader.dto.UpdateProgressRequest;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.entity.EbookReadingProgress;
import com.zx.reader.progress.ProgressFlushMessage;
import com.zx.reader.progress.ProgressHotState;
import com.zx.reader.progress.ProgressRedisStore;
import com.zx.reader.progress.ReaderProgressMqProducer;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.EbookReadingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingProgressServiceTest {

    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;
    @Mock
    private EbookReadingProgressRepository progressRepository;
    @Mock
    private ProgressRedisStore progressRedisStore;
    @Mock
    private ReaderProgressMqProducer progressMqProducer;

    private ChapterAccessService chapterAccessService;
    private ReaderProperties readerProperties;
    private ReadingProgressService service;

    @BeforeEach
    void setUp() {
        chapterAccessService = new ChapterAccessService(new MediaAccessService(
                org.mockito.Mockito.mock(com.zx.bookstore.borrow.repository.BorrowOrderRepository.class),
                org.mockito.Mockito.mock(com.zx.bookstore.trade.repository.TradeOrderRepository.class)));
        readerProperties = new ReaderProperties();
        readerProperties.getProgress().setCoalesceEnabled(true);
        readerProperties.getProgress().setIdleMs(5_000L);
        readerProperties.getProgress().setDelayMs(10_000L);
        service = new ReadingProgressService(
                ebookBookRepository, ebookChapterRepository, progressRepository,
                chapterAccessService, progressRedisStore, progressMqProducer, readerProperties);
    }

    @Test
    void firstProgress_shouldPersistImmediatelyWithoutMq() {
        stubOnlineBook();
        stubPreviewChapter(101L, 1);
        when(progressRedisStore.get(1L, 10L)).thenReturn(Optional.empty());
        when(progressRepository.findByUserAndEbook(1L, 10L)).thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(progressRedisStore.touchAndMarkDirty(eq(1L), eq(10L), eq(101L), eq(12), anyLong()))
                .thenReturn(true);

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setChapterNo(1);
        req.setCharOffset(12);

        ReadingProgressResponse resp = service.updateProgress(1L, 10L, req);

        assertThat(resp.getChapterId()).isEqualTo(101L);
        assertThat(resp.getCharOffset()).isEqualTo(12);
        verify(progressRepository).save(any(EbookReadingProgress.class));
        verify(progressMqProducer, never()).scheduleFlush(anyLong(), anyLong());
        verify(progressRedisStore).markClean(1L, 10L);
    }

    @Test
    void sameChapterOffset_shouldCoalesceAndScheduleOnce() {
        stubOnlineBook();
        stubPreviewChapter(101L, 1);
        when(progressRedisStore.get(1L, 10L))
                .thenReturn(Optional.of(new ProgressHotState(101L, 10, System.currentTimeMillis(), true, true)));
        when(progressRedisStore.touchAndMarkDirty(eq(1L), eq(10L), eq(101L), eq(50), anyLong()))
                .thenReturn(true);

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setChapterId(101L);
        req.setCharOffset(50);

        service.updateProgress(1L, 10L, req);

        verify(progressRepository, never()).save(any());
        verify(progressMqProducer).scheduleFlush(1L, 10L);
    }

    @Test
    void chapterChange_shouldPersistImmediately() {
        stubOnlineBook();
        EbookChapter ch2 = new EbookChapter();
        ch2.setId(102L);
        ch2.setEbookId(10L);
        ch2.setChapterNo(2);
        ch2.setIsPreviewFree(1);
        when(ebookChapterRepository.findById(102L)).thenReturn(Optional.of(ch2));
        when(progressRedisStore.get(1L, 10L))
                .thenReturn(Optional.of(new ProgressHotState(101L, 99, System.currentTimeMillis(), false, false)));
        when(progressRepository.findByUserAndEbook(1L, 10L)).thenReturn(Optional.of(existing(101L, 99)));
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(progressRedisStore.touchAndMarkDirty(eq(1L), eq(10L), eq(102L), eq(0), anyLong()))
                .thenReturn(true);

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setChapterId(102L);
        req.setCharOffset(0);

        service.updateProgress(1L, 10L, req);

        ArgumentCaptor<EbookReadingProgress> captor = ArgumentCaptor.forClass(EbookReadingProgress.class);
        verify(progressRepository).save(captor.capture());
        assertThat(captor.getValue().getChapterId()).isEqualTo(102L);
        verify(progressMqProducer, never()).scheduleFlush(anyLong(), anyLong());
    }

    @Test
    void onFlushDue_whenStillActive_shouldReschedule() {
        long now = System.currentTimeMillis();
        when(progressRedisStore.get(1L, 10L))
                .thenReturn(Optional.of(new ProgressHotState(101L, 40, now - 1000L, true, true)));

        service.onFlushDue(new ProgressFlushMessage(1L, 10L));

        verify(progressRepository, never()).save(any());
        verify(progressRedisStore).markPendingFlush(1L, 10L);
        verify(progressMqProducer).scheduleFlush(1L, 10L);
    }

    @Test
    void onFlushDue_whenIdle_shouldPersist() {
        long now = System.currentTimeMillis();
        when(progressRedisStore.get(1L, 10L))
                .thenReturn(Optional.of(new ProgressHotState(101L, 40, now - 10_000L, true, true)));
        when(progressRepository.findByUserAndEbook(1L, 10L)).thenReturn(Optional.of(existing(101L, 10)));
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onFlushDue(new ProgressFlushMessage(1L, 10L));

        verify(progressRepository).save(any());
        verify(progressRedisStore).markClean(1L, 10L);
        verify(progressMqProducer, never()).scheduleFlush(anyLong(), anyLong());
    }

    @Test
    void lockedChapter_shouldDenyProgress() {
        stubOnlineBook();
        EbookChapter locked = new EbookChapter();
        locked.setId(103L);
        locked.setEbookId(10L);
        locked.setChapterNo(3);
        locked.setIsPreviewFree(0);
        when(ebookChapterRepository.findById(103L)).thenReturn(Optional.of(locked));

        UpdateProgressRequest req = new UpdateProgressRequest();
        req.setChapterId(103L);
        req.setCharOffset(1);

        assertThatThrownBy(() -> service.updateProgress(1L, 10L, req))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_PREVIEW_DENIED));
        verify(progressRedisStore, never()).touchAndMarkDirty(anyLong(), anyLong(), anyLong(), anyInt(), anyLong());
    }

    private void stubOnlineBook() {
        EbookBook book = new EbookBook();
        book.setId(10L);
        book.setStatus(1);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
    }

    private void stubPreviewChapter(Long id, int no) {
        EbookChapter ch = new EbookChapter();
        ch.setId(id);
        ch.setEbookId(10L);
        ch.setChapterNo(no);
        ch.setIsPreviewFree(1);
        when(ebookChapterRepository.findById(id)).thenReturn(Optional.of(ch));
        org.mockito.Mockito.lenient()
                .when(ebookChapterRepository.findByEbookIdAndChapterNo(10L, no))
                .thenReturn(Optional.of(ch));
    }

    private static EbookReadingProgress existing(Long chapterId, int offset) {
        EbookReadingProgress p = new EbookReadingProgress();
        p.setId(1L);
        p.setUserId(1L);
        p.setEbookId(10L);
        p.setChapterId(chapterId);
        p.setCharOffset(offset);
        return p;
    }
}
