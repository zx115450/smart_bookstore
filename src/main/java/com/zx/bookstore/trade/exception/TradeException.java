package com.zx.bookstore.trade.exception;

public class TradeException extends RuntimeException {

    private final int code;

    public TradeException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TradeException orderNotFound() {
        return new TradeException(4201, "购书订单不存在");
    }

    public static TradeException invalidStatus() {
        return new TradeException(4202, "订单状态不允许该操作");
    }

    public static TradeException insufficientBalance() {
        return new TradeException(4203, "余额不足");
    }

    public static TradeException outOfStock() {
        return new TradeException(4204, "库存不足");
    }

    public static TradeException forbidden() {
        return new TradeException(4205, "无权操作他人订单");
    }

    public static TradeException bookNotFound() {
        return new TradeException(4206, "图书不存在或已下架");
    }
}
