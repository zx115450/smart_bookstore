package com.zx.bookstore.borrow.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import com.zx.bookstore.borrow.enums.BorrowOrderStatus;
import com.zx.bookstore.borrow.mapper.BorrowOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BorrowOrderRepository {

    private final BorrowOrderMapper mapper;

    public Optional<BorrowOrder> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public long countOccupiedByBookId(Long bookId) {
        Long count = mapper.selectCount(
                Wrappers.<BorrowOrder>lambdaQuery()
                        .eq(BorrowOrder::getBookId, bookId)
                        .in(BorrowOrder::getStatus,
                                BorrowOrderStatus.APPLIED.name(),
                                BorrowOrderStatus.BORROWED.name(),
                                BorrowOrderStatus.OVERDUE.name())
        );
        return count == null ? 0 : count;
    }

    public boolean hasActiveBorrowByUser(Long userId) {
        Long count = mapper.selectCount(
                Wrappers.<BorrowOrder>lambdaQuery()
                        .eq(BorrowOrder::getUserId, userId)
                        .in(BorrowOrder::getStatus,
                                BorrowOrderStatus.BORROWED.name(),
                                BorrowOrderStatus.OVERDUE.name())
        );
        return count != null && count > 0;
    }

    /**
     * 用户对该书是否处于借阅中或逾期未还（占额度 / 待还统计用）。
     * <p>
     * 线上阅读与完整视频解锁请用 {@link #hasUnlockBorrow(Long, Long)}：逾期应降级试看。
     */
    public boolean hasActiveBorrow(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return false;
        }
        Long count = mapper.selectCount(
                Wrappers.<BorrowOrder>lambdaQuery()
                        .eq(BorrowOrder::getUserId, userId)
                        .eq(BorrowOrder::getBookId, bookId)
                        .in(BorrowOrder::getStatus,
                                BorrowOrderStatus.BORROWED.name(),
                                BorrowOrderStatus.OVERDUE.name())
        );
        return count != null && count > 0;
    }

    /**
     * 是否可解锁付费章 / 完整配套视频：仅 {@code BORROWED}。
     * {@code OVERDUE} 不解锁（归还前降级试看）；已购走交易侧判定。
     */
    public boolean hasUnlockBorrow(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return false;
        }
        Long count = mapper.selectCount(
                Wrappers.<BorrowOrder>lambdaQuery()
                        .eq(BorrowOrder::getUserId, userId)
                        .eq(BorrowOrder::getBookId, bookId)
                        .eq(BorrowOrder::getStatus, BorrowOrderStatus.BORROWED.name())
        );
        return count != null && count > 0;
    }

    public List<BorrowOrder> pageByUser(Long userId, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<BorrowOrder>lambdaQuery()
                .eq(BorrowOrder::getUserId, userId)
                .orderByDesc(BorrowOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(BorrowOrder::getStatus, status);
        }
        return mapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countByUser(Long userId, String status) {
        var wrapper = Wrappers.<BorrowOrder>lambdaQuery().eq(BorrowOrder::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(BorrowOrder::getStatus, status);
        }
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public List<BorrowOrder> pageAll(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<BorrowOrder>lambdaQuery()
                .orderByDesc(BorrowOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(BorrowOrder::getStatus, status);
        }
        return mapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countAll(String status) {
        var wrapper = Wrappers.<BorrowOrder>lambdaQuery();
        if (status != null && !status.isBlank()) {
            wrapper.eq(BorrowOrder::getStatus, status);
        }
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public BorrowOrder save(BorrowOrder order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getId() == null) {
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(now);
            }
            order.setUpdatedAt(now);
            mapper.insert(order);
            return order;
        }
        order.setUpdatedAt(now);
        mapper.updateById(order);
        return order;
    }

    public int updateToBorrowed(Long id, LocalDateTime borrowAt, LocalDateTime dueAt) {
        return mapper.updateToBorrowed(id, borrowAt, dueAt);
    }

    public int updateToCancelled(Long id) {
        return mapper.updateToCancelled(id);
    }

    public int updateToReturned(Long id, LocalDateTime returnAt) {
        return mapper.updateToReturned(id, returnAt);
    }

    public int updateToOverdue(Long id) {
        return mapper.updateToOverdue(id);
    }

    public int updateOverdueBatch(int limit) {
        return mapper.updateOverdueBatch(limit);
    }

    public List<Map<String, Object>> borrowTrend(LocalDateTime start) {
        return mapper.borrowTrend(start);
    }

    public List<Map<String, Object>> borrowByStatus() {
        return mapper.borrowByStatus();
    }

    public long todayBorrowCount() {
        return mapper.todayBorrowCount();
    }

    /** 推荐热度：按 book_id 聚合借阅单数（不限状态，历史借阅均计入），TopN。 */
    public List<Map<String, Object>> findHotBorrowBooks(int limit) {
        int safe = Math.min(Math.max(limit, 1), 200);
        return mapper.findHotBorrowBooks(safe);
    }

    /**
     * 共现召回：与目标用户借过相同书的其他用户，再聚合他们借过的其他书（排除目标用户自己的书）。
     * 用于个性化推荐（H 板块）。bookIds 为空时返回空列表。
     */
    public List<Map<String, Object>> findCoBorrowedBooks(Long userId, List<Long> bookIds, int limit) {
        if (userId == null || bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }
        int safe = Math.min(Math.max(limit, 1), 200);
        return mapper.findCoBorrowedBooks(userId, bookIds, safe);
    }
}
