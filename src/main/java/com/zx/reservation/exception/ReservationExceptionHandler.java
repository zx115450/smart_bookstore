package com.zx.reservation.exception;

import com.zx.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.zx.reservation.controller")
public class ReservationExceptionHandler {

    @ExceptionHandler(ReservationException.class)
    public ApiResponse<Void> handleReservationException(ReservationException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error(1002, ex.getMessage());
    }
}
