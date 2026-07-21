package com.zx.marketing.checkin.exception;

import com.zx.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 签到模块特异性异常处理。
 */
@RestControllerAdvice(basePackages = "com.zx.marketing.checkin.controller")
public class CheckinExceptionHandler {

    @ExceptionHandler(CheckinException.class)
    public ApiResponse<Void> handleCheckinException(CheckinException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
