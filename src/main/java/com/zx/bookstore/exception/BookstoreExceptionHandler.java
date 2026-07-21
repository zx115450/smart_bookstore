package com.zx.bookstore.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 书城目录模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.bookstore.catalog.controller")
public class BookstoreExceptionHandler {

    @ExceptionHandler(BookstoreException.class)
    public ApiResponse<Void> handleBookstoreException(BookstoreException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
