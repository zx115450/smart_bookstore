package com.zx.reservation.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 预约模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.reservation.controller")
public class ReservationExceptionHandler {

    @ExceptionHandler(ReservationException.class)
    public ApiResponse<Void> handleReservationException(ReservationException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
