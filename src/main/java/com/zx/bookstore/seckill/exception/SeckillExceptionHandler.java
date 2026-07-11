package com.zx.bookstore.seckill.exception;

import com.zx.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.zx.bookstore.seckill.controller")
public class SeckillExceptionHandler {

    @ExceptionHandler(SeckillException.class)
    public ApiResponse<Void> handleSeckillException(SeckillException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(1002, ex.getMessage());
    }
}
