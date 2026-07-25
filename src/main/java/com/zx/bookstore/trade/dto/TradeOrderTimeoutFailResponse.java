package com.zx.bookstore.trade.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TradeOrderTimeoutFailResponse {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String failReason;
    private String status;
    private LocalDateTime createdAt;
}
