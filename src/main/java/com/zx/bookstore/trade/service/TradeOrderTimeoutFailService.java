package com.zx.bookstore.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutFailResponse;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.entity.TradeOrderTimeoutFail;
import com.zx.bookstore.trade.mapper.TradeOrderTimeoutFailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单 DLQ 仍失败时的落库与查询，避免消息 Nack 丢弃后无迹可查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeOrderTimeoutFailService {

    private static final int FAIL_REASON_MAX = 500;

    private final TradeOrderTimeoutFailMapper failMapper;

    /**
     * 记录一次 DLQ 关单失败。落库成功后应由调用方 Ack 消息。
     */
    public void recordFailure(TradeOrderTimeoutMessage message, Throwable error) {
        TradeOrderTimeoutFail row = new TradeOrderTimeoutFail();
        if (message != null) {
            row.setOrderId(message.getOrderId());
            row.setOrderNo(message.getOrderNo());
        }
        row.setFailReason(truncate(resolveReason(error)));
        row.setStatus(TradeOrderTimeoutFail.STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        failMapper.insert(row);
        log.warn("recorded trade timeout cancel failure, id={}, orderId={}, orderNo={}",
                row.getId(), row.getOrderId(), row.getOrderNo());
    }

    public PageResult<TradeOrderTimeoutFailResponse> list(String status, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (safePage - 1) * safeSize;

        LambdaQueryWrapper<TradeOrderTimeoutFail> countQw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            countQw.eq(TradeOrderTimeoutFail::getStatus, status.trim());
        }
        Long total = failMapper.selectCount(countQw);

        LambdaQueryWrapper<TradeOrderTimeoutFail> listQw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            listQw.eq(TradeOrderTimeoutFail::getStatus, status.trim());
        }
        listQw.orderByDesc(TradeOrderTimeoutFail::getId)
                .last("LIMIT " + offset + "," + safeSize);
        List<TradeOrderTimeoutFailResponse> records = failMapper.selectList(listQw).stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(safePage, safeSize, total == null ? 0 : total, records);
    }

    private TradeOrderTimeoutFailResponse toResponse(TradeOrderTimeoutFail row) {
        TradeOrderTimeoutFailResponse resp = new TradeOrderTimeoutFailResponse();
        resp.setId(row.getId());
        resp.setOrderId(row.getOrderId());
        resp.setOrderNo(row.getOrderNo());
        resp.setFailReason(row.getFailReason());
        resp.setStatus(row.getStatus());
        resp.setCreatedAt(row.getCreatedAt());
        return resp;
    }

    private static String resolveReason(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String msg = error.getMessage();
        if (!StringUtils.hasText(msg)) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + msg;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "unknown";
        }
        if (text.length() <= FAIL_REASON_MAX) {
            return text;
        }
        return text.substring(0, FAIL_REASON_MAX);
    }
}
