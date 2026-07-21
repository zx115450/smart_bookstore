package com.zx.ai.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 模块特异性异常处理。
 * <p>
 * 通用异常（如 {@link IllegalArgumentException}、未知异常）由
 * {@link com.zx.common.exception.GlobalExceptionHandler} 兜底。
 */
@RestControllerAdvice(basePackages = "com.zx.ai.controller")
public class AiExceptionHandler {

    @ExceptionHandler(AiException.class)
    public ApiResponse<Void> handleAiException(AiException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
