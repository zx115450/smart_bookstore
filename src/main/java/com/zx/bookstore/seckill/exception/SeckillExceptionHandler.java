package com.zx.bookstore.seckill.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 秒杀模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.bookstore.seckill.controller")
public class SeckillExceptionHandler {

    @ExceptionHandler(SeckillException.class)
    public ApiResponse<Void> handleSeckillException(SeckillException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
