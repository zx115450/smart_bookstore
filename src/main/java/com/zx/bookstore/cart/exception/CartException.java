package com.zx.bookstore.cart.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class CartException extends BusinessException {

    public CartException(int code, String message) {
        super(code, message);
    }

    public static CartException itemNotFound() {
        return new CartException(ErrorCode.CART_ITEM_NOT_FOUND, "购物车项不存在");
    }

    public static CartException forbidden() {
        return new CartException(ErrorCode.CART_FORBIDDEN, "无权操作他人购物车");
    }

    public static CartException bookNotFound() {
        return new CartException(ErrorCode.CART_BOOK_NOT_FOUND, "图书不存在或已下架");
    }

    public static CartException outOfStock() {
        return new CartException(ErrorCode.CART_OUT_OF_STOCK, "库存不足");
    }

    public static CartException emptyCart() {
        return new CartException(ErrorCode.CART_EMPTY, "购物车为空");
    }
}
