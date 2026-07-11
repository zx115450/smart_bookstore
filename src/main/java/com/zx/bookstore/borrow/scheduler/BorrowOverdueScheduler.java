package com.zx.bookstore.borrow.scheduler;

import com.zx.bookstore.borrow.config.BookstoreBorrowOverdueProperties;
import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.borrow.service.BorrowDueRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 借阅逾期：ZSET + Lua 弹出到期单，同步 UPDATE MySQL；辅以 SQL 对账兜底（ZADD 失败等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bookstore.borrow.overdue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BorrowOverdueScheduler {

    private final BookstoreBorrowOverdueProperties properties;
    private final BorrowDueRedisService borrowDueRedisService;
    private final BorrowOrderRepository borrowOrderRepository;

    @Scheduled(fixedDelayString = "${bookstore.borrow.overdue.poll-interval-ms:60000}")
    public void processOverdue() {
        int batchSize = Math.max(1, properties.getBatchSize());
        long nowEpoch = Instant.now().getEpochSecond();

        List<Long> orderIds = borrowDueRedisService.popDueOrderIds(nowEpoch, batchSize);
        int marked = 0;
        for (Long orderId : orderIds) {
            if (borrowOrderRepository.updateToOverdue(orderId) > 0) {
                marked++;
            }
        }
        if (marked > 0) {
            log.info("borrow overdue marked from zset, count={}", marked);
        }

        reconcileOverdueBatch(batchSize);
    }

    /** 兜底：Redis ZADD 失败或历史数据未入 ZSET 时，直接从 DB 标记逾期。 */
    private void reconcileOverdueBatch(int batchSize) {
        int total = 0;
        int updated;
        do {
            updated = borrowOrderRepository.updateOverdueBatch(batchSize);
            total += updated;
        } while (updated == batchSize);
        if (total > 0) {
            log.info("borrow overdue reconciled from db, count={}", total);
        }
    }
}
