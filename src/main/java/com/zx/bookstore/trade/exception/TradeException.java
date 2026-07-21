package com.zx.bookstore.trade.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class TradeException extends BusinessException {

    public TradeException(int code, String message) {
        super(code, message);
    }

    public static TradeException orderNotFound() {
        return new TradeException(ErrorCode.TRADE_ORDER_NOT_FOUND, "购书订单不存在");
    }

    public static TradeException invalidStatus() {
        return new TradeException(ErrorCode.TRADE_INVALID_STATUS, "订单状态不允许该操作");
    }

    public static TradeException insufficientBalance() {
        return new TradeException(ErrorCode.TRADE_INSUFFICIENT_BALANCE, "余额不足");
    }

    public static TradeException outOfStock() {
        return new TradeException(ErrorCode.TRADE_OUT_OF_STOCK, "库存不足");
    }

    public static TradeException forbidden() {
        return new TradeException(ErrorCode.TRADE_FORBIDDEN, "无权操作他人订单");
    }

    public static TradeException bookNotFound() {
        return new TradeException(ErrorCode.TRADE_BOOK_NOT_FOUND, "图书不存在或已下架");
    }
}
