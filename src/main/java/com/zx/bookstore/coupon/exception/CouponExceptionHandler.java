package com.zx.bookstore.coupon.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 优惠券模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.bookstore.coupon.controller")
public class CouponExceptionHandler {

    @ExceptionHandler(CouponException.class)
    public ApiResponse<Void> handleCouponException(CouponException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
