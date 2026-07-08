package com.zx.bookstore.trade.exception;

import com.zx.auth.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.zx.bookstore.trade.controller")
public class TradeExceptionHandler {

    @ExceptionHandler(TradeException.class)
    public ApiResponse<Void> handleTradeException(TradeException ex) {
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
}
