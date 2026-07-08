package com.zx.bookstore.trade.enums;

public enum TradeOrderStatus {
    PENDING_PAY,
    PAID,
    CANCELLED,
    COMPLETED;

    public static TradeOrderStatus from(String status) {
        return TradeOrderStatus.valueOf(status);
    }
}
