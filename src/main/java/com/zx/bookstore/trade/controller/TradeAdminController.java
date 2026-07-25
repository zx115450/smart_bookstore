package com.zx.bookstore.trade.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutFailResponse;
import com.zx.bookstore.trade.service.TradeOrderTimeoutFailService;
import com.zx.bookstore.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/trade")
@RequiredArgsConstructor
public class TradeAdminController {

    private final TradeService tradeService;
    private final TradeOrderTimeoutFailService timeoutFailService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ApiResponse<PageResult<TradeOrderResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(tradeService.listAllOrders(status, page, size));
    }

    /** 超时关单 DLQ 仍失败后的落库记录（PENDING 待补偿）。 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/timeout-fails")
    public ApiResponse<PageResult<TradeOrderTimeoutFailResponse>> listTimeoutFails(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(timeoutFailService.list(status, page, size));
    }
}
