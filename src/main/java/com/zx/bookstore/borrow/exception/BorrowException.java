package com.zx.bookstore.borrow.exception;

public class BorrowException extends RuntimeException {

    private final int code;

    public BorrowException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BorrowException outOfStock() {
        return new BorrowException(4002, "可借册数不足");
    }

    public static BorrowException hasUnreturned() {
        return new BorrowException(4003, "存在未还书籍，不可再借");
    }

    public static BorrowException invalidStatus() {
        return new BorrowException(4004, "借阅单状态不允许该操作");
    }

    public static BorrowException orderNotFound() {
        return new BorrowException(4006, "借阅单不存在");
    }

    public static BorrowException forbidden() {
        return new BorrowException(4007, "无权操作他人借阅单");
    }
}
