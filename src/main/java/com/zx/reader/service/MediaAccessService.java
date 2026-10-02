package com.zx.reader.service;

import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 实体书借阅 / 已购解锁（阅读章文与配套视频共用）。
 */
@Service
@RequiredArgsConstructor
public class MediaAccessService {

    private final BorrowOrderRepository borrowOrderRepository;
    private final TradeOrderRepository tradeOrderRepository;

    /**
     * 是否可完整观看图书配套视频 / 解锁付费章。
     * <ul>
     *   <li>借阅中：仅 {@code BORROWED}（{@code OVERDUE} 降级试看）</li>
     *   <li>已购：交易单 {@code PAID} 且含该 bookId（永久，不受归还/逾期影响）</li>
     *   <li>ADMIN：演示联调放行</li>
     * </ul>
     */
    public boolean canWatchFullMedia(Long userId, Long bookId, Collection<String> roles) {
        if (roles != null && roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r))) {
            return true;
        }
        return canWatchFullMedia(userId, bookId);
    }

    public boolean canWatchFullMedia(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return false;
        }
        if (borrowOrderRepository.hasUnlockBorrow(userId, bookId)) {
            return true;
        }
        return tradeOrderRepository.hasPaidBook(userId, bookId);
    }
}
