package com.zx.reader.service;

import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterAccessServiceTest {

    @Mock
    private BorrowOrderRepository borrowOrderRepository;
    @Mock
    private TradeOrderRepository tradeOrderRepository;

    private ChapterAccessService service;

    @BeforeEach
    void setUp() {
        MediaAccessService mediaAccessService = new MediaAccessService(borrowOrderRepository, tradeOrderRepository);
        service = new ChapterAccessService(mediaAccessService);
    }

    @Test
    void previewFreeChapterIsReadable() {
        EbookChapter ch = new EbookChapter();
        ch.setIsPreviewFree(1);
        assertThat(service.canReadChapter(1L, new EbookBook(), ch)).isTrue();
    }

    @Test
    void lockedChapterDeniedWhenNoBorrowOrPurchase() {
        EbookBook book = new EbookBook();
        book.setBookId(99L);
        EbookChapter ch = new EbookChapter();
        ch.setIsPreviewFree(0);
        when(borrowOrderRepository.hasUnlockBorrow(1L, 99L)).thenReturn(false);
        when(tradeOrderRepository.hasPaidBook(1L, 99L)).thenReturn(false);
        assertThat(service.canReadChapter(1L, book, ch)).isFalse();
    }

    @Test
    void lockedChapterReadableWhenBorrowed() {
        EbookBook book = new EbookBook();
        book.setBookId(99L);
        EbookChapter ch = new EbookChapter();
        ch.setIsPreviewFree(0);
        when(borrowOrderRepository.hasUnlockBorrow(1L, 99L)).thenReturn(true);
        assertThat(service.canReadChapter(1L, book, ch)).isTrue();
    }

    @Test
    void lockedChapterDeniedWhenOverdue() {
        EbookBook book = new EbookBook();
        book.setBookId(99L);
        EbookChapter ch = new EbookChapter();
        ch.setIsPreviewFree(0);
        when(borrowOrderRepository.hasUnlockBorrow(1L, 99L)).thenReturn(false);
        when(tradeOrderRepository.hasPaidBook(1L, 99L)).thenReturn(false);
        assertThat(service.canReadChapter(1L, book, ch)).isFalse();
    }

    @Test
    void adminRoleUnlockedViaMediaAccess() {
        MediaAccessService mediaAccessService = new MediaAccessService(borrowOrderRepository, tradeOrderRepository);
        assertThat(mediaAccessService.canWatchFullMedia(1L, 99L, List.of("ADMIN"))).isTrue();
    }
}
