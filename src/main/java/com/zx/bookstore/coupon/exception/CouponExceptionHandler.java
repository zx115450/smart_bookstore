package com.zx.bookstore.coupon.exception;

import com.zx.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.zx.bookstore.coupon.controller")
public class CouponExceptionHandler {

    @ExceptionHandler(CouponException.class)
    public ApiResponse<Void> handleCouponException(CouponException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
