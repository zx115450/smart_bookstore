package com.zx.reader.service;

import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaAccessServiceTest {

    @Mock
    private BorrowOrderRepository borrowOrderRepository;
    @Mock
    private TradeOrderRepository tradeOrderRepository;

    private MediaAccessService service;

    @BeforeEach
    void setUp() {
        service = new MediaAccessService(borrowOrderRepository, tradeOrderRepository);
    }

    @Test
    void unlockedWhenBorrowed() {
        when(borrowOrderRepository.hasUnlockBorrow(1L, 10L)).thenReturn(true);
        assertThat(service.canWatchFullMedia(1L, 10L)).isTrue();
        verifyNoInteractions(tradeOrderRepository);
    }

    @Test
    void unlockedWhenPaid() {
        when(borrowOrderRepository.hasUnlockBorrow(1L, 10L)).thenReturn(false);
        when(tradeOrderRepository.hasPaidBook(1L, 10L)).thenReturn(true);
        assertThat(service.canWatchFullMedia(1L, 10L)).isTrue();
    }

    @Test
    void lockedWhenReturnedAndNotPaid() {
        when(borrowOrderRepository.hasUnlockBorrow(1L, 10L)).thenReturn(false);
        when(tradeOrderRepository.hasPaidBook(1L, 10L)).thenReturn(false);
        assertThat(service.canWatchFullMedia(1L, 10L)).isFalse();
    }

    @Test
    void adminAlwaysUnlocked() {
        assertThat(service.canWatchFullMedia(1L, 10L, List.of("USER", "ADMIN"))).isTrue();
        verifyNoInteractions(borrowOrderRepository, tradeOrderRepository);
    }
}
