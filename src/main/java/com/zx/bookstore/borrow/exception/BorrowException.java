package com.zx.bookstore.borrow.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class BorrowException extends BusinessException {

    public BorrowException(int code, String message) {
        super(code, message);
    }

    public static BorrowException outOfStock() {
        return new BorrowException(ErrorCode.BORROW_OUT_OF_STOCK, "可借册数不足");
    }

    public static BorrowException hasUnreturned() {
        return new BorrowException(ErrorCode.BORROW_HAS_UNRETURNED, "存在未还书籍，不可再借");
    }

    public static BorrowException invalidStatus() {
        return new BorrowException(ErrorCode.BORROW_INVALID_STATUS, "借阅单状态不允许该操作");
    }

    public static BorrowException orderNotFound() {
        return new BorrowException(ErrorCode.BORROW_ORDER_NOT_FOUND, "借阅单不存在");
    }

    public static BorrowException forbidden() {
        return new BorrowException(ErrorCode.BORROW_FORBIDDEN, "无权操作他人借阅单");
    }
}
