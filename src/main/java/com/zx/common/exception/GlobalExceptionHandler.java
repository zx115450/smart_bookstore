package com.zx.common.exception;

import com.zx.auth.service.JwtService;
import com.zx.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常处理器，作为各模块特异性处理的兜底。
 * <p>
 * 处理顺序：优先匹配模块级 {@code @RestControllerAdvice} 中的具体异常；
 * 未命中时由本处理器统一转换为 {@link ApiResponse}。
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(JwtService.TokenExpiredException.class)
    public ApiResponse<Void> handleTokenExpired(JwtService.TokenExpiredException ex) {
        return ApiResponse.error(ErrorCode.TOKEN_EXPIRED, ex.getMessage());
    }

    @ExceptionHandler(JwtService.InvalidAccessTokenException.class)
    public ApiResponse<Void> handleInvalidToken(JwtService.InvalidAccessTokenException ex) {
        return ApiResponse.error(ErrorCode.TOKEN_INVALID, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
        return ApiResponse.error(ErrorCode.ACCESS_DENIED, "权限不足，无法访问该资源");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(ErrorCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.error(ErrorCode.UNKNOWN_ERROR, "系统繁忙，请稍后重试");
    }
}
