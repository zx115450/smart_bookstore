package com.zx.bookstore.trade.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ApiResponse<PageResult<TradeOrderResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(tradeService.listAllOrders(status, page, size));
    }
}
