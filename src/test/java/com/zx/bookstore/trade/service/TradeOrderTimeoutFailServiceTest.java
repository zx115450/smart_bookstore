package com.zx.bookstore.trade.service;

import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.entity.TradeOrderTimeoutFail;
import com.zx.bookstore.trade.mapper.TradeOrderTimeoutFailMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeOrderTimeoutFailServiceTest {

    @Mock
    private TradeOrderTimeoutFailMapper failMapper;

    @InjectMocks
    private TradeOrderTimeoutFailService failService;

    @Test
    void recordFailure_shouldInsertPendingRow() {
        TradeOrderTimeoutMessage message = new TradeOrderTimeoutMessage(9L, "T202601010001");
        failService.recordFailure(message, new IllegalStateException("db down"));

        ArgumentCaptor<TradeOrderTimeoutFail> captor = ArgumentCaptor.forClass(TradeOrderTimeoutFail.class);
        verify(failMapper).insert(captor.capture());
        TradeOrderTimeoutFail row = captor.getValue();
        assertEquals(9L, row.getOrderId());
        assertEquals("T202601010001", row.getOrderNo());
        assertEquals(TradeOrderTimeoutFail.STATUS_PENDING, row.getStatus());
        assertTrue(row.getFailReason().contains("db down"));
        assertNotNull(row.getCreatedAt());
    }
}
