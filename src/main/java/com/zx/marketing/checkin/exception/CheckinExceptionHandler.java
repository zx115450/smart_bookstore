package com.zx.marketing.checkin.exception;

import com.zx.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.zx.marketing.checkin.controller")
public class CheckinExceptionHandler {

    @ExceptionHandler(CheckinException.class)
    public ApiResponse<Void> handleCheckinException(CheckinException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
