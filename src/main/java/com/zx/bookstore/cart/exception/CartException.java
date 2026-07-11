package com.zx.bookstore.cart.exception;

public class CartException extends RuntimeException {

    private final int code;

    public CartException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CartException itemNotFound() {
        return new CartException(4151, "购物车项不存在");
    }

    public static CartException forbidden() {
        return new CartException(4152, "无权操作他人购物车");
    }

    public static CartException bookNotFound() {
        return new CartException(4153, "图书不存在或已下架");
    }

    public static CartException outOfStock() {
        return new CartException(4154, "库存不足");
    }

    public static CartException emptyCart() {
        return new CartException(4155, "购物车为空");
    }
}
