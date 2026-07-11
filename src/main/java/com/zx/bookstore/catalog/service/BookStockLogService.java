package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.BookStockLogResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.entity.BookStockLog;
import com.zx.bookstore.catalog.enums.StockChangeType;
import com.zx.bookstore.catalog.repository.BookStockLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookStockLogService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String REF_BORROW_ORDER = "borrow_order";
    public static final String REF_TRADE_ORDER = "trade_order";
    public static final String REF_ADMIN = "admin";

    private final BookStockLogRepository bookStockLogRepository;

    public void recordBorrowOut(Long bookId, int qty, Long borrowOrderId, Long operatorId) {
        append(StockChangeType.BORROW_OUT, bookId, qty, REF_BORROW_ORDER, borrowOrderId, operatorId, null);
    }

    public void recordBorrowIn(Long bookId, int qty, Long borrowOrderId, Long operatorId) {
        append(StockChangeType.BORROW_IN, bookId, qty, REF_BORROW_ORDER, borrowOrderId, operatorId, null);
    }

    public void recordSaleOut(Long bookId, int qty, Long tradeOrderId, Long operatorId) {
        append(StockChangeType.SALE_OUT, bookId, qty, REF_TRADE_ORDER, tradeOrderId, operatorId, null);
    }

    public void recordAdminAdjust(Long bookId, int qty, Long operatorId, String remark) {
        append(StockChangeType.ADMIN_ADJUST, bookId, qty, REF_ADMIN, null, operatorId, remark);
    }

    public PageResult<BookStockLogResponse> listByBookId(Long bookId, long page, long size) {
        List<BookStockLogResponse> records = bookStockLogRepository.pageByBookId(bookId, page, size)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        return new PageResult<>(safePage, safeSize, bookStockLogRepository.countByBookId(bookId), records);
    }

    private void append(StockChangeType changeType, Long bookId, int qty,
                        String refType, Long refId, Long operatorId, String remark) {
        if (bookId == null || qty <= 0) {
            return;
        }
        BookStockLog log = new BookStockLog();
        log.setBookId(bookId);
        log.setChangeType(changeType.name());
        log.setChangeQty(qty);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setOperatorId(operatorId);
        if (StringUtils.hasText(remark)) {
            log.setRemark(remark.length() > 255 ? remark.substring(0, 255) : remark);
        }
        bookStockLogRepository.save(log);
    }

    private BookStockLogResponse toResponse(BookStockLog log) {
        BookStockLogResponse resp = new BookStockLogResponse();
        resp.setId(log.getId());
        resp.setBookId(log.getBookId());
        resp.setChangeType(log.getChangeType());
        resp.setChangeQty(log.getChangeQty());
        resp.setRefType(log.getRefType());
        resp.setRefId(log.getRefId());
        resp.setOperatorId(log.getOperatorId());
        resp.setRemark(log.getRemark());
        resp.setCreatedAt(log.getCreatedAt() == null ? null : log.getCreatedAt().format(DATETIME_FMT));
        return resp;
    }
}
