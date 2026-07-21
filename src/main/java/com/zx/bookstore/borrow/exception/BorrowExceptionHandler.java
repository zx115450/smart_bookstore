package com.zx.bookstore.borrow.exception;

import com.zx.bookstore.exception.BookstoreException;
import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 借阅模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.bookstore.borrow.controller")
public class BorrowExceptionHandler {

    @ExceptionHandler(BorrowException.class)
    public ApiResponse<Void> handleBorrowException(BorrowException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BookstoreException.class)
    public ApiResponse<Void> handleBookstoreException(BookstoreException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
