package com.zx.bookstore.cart.exception;

import com.zx.auth.dto.ApiResponse;
import com.zx.bookstore.exception.BookstoreException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(1002, ex.getMessage());
    }
}
