package com.zx.bookstore.catalog.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.catalog.entity.BookStockLog;
import com.zx.bookstore.catalog.mapper.BookStockLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookStockLogRepository {

    private final BookStockLogMapper mapper;

    public BookStockLog save(BookStockLog log) {
        LocalDateTime now = LocalDateTime.now();
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(now);
        }
        mapper.insert(log);
        return log;
    }

    public List<BookStockLog> pageByBookId(Long bookId, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        return mapper.selectList(
                Wrappers.<BookStockLog>lambdaQuery()
                        .eq(BookStockLog::getBookId, bookId)
                        .orderByDesc(BookStockLog::getId)
                        .last("LIMIT " + safeSize + " OFFSET " + offset)
        );
    }

    public long countByBookId(Long bookId) {
        Long count = mapper.selectCount(
                Wrappers.<BookStockLog>lambdaQuery().eq(BookStockLog::getBookId, bookId)
        );
        return count == null ? 0 : count;
    }
}
