package com.zx.bookstore.cart.exception;

import com.zx.bookstore.exception.BookstoreException;
import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 购物车模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.bookstore.cart.controller")
public class CartExceptionHandler {

    @ExceptionHandler(CartException.class)
    public ApiResponse<Void> handleCartException(CartException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BookstoreException.class)
    public ApiResponse<Void> handleBookstoreException(BookstoreException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
