package com.zx.bookstore.trade.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.trade.dto.CreateTradeOrderRequest;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
import com.zx.bookstore.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders")
    public ApiResponse<TradeOrderResponse> createOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody CreateTradeOrderRequest req
    ) {
        return ApiResponse.ok(tradeService.createOrder(principal, req));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders/{id}/pay")
    public ApiResponse<TradeOrderResponse> pay(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(tradeService.pay(principal, id));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<TradeOrderResponse> cancel(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(tradeService.cancel(principal, id));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/mine")
    public ApiResponse<PageResult<TradeOrderResponse>> listMyOrders(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(tradeService.listMyOrders(principal, status, page, size));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/{id}")
    public ApiResponse<TradeOrderResponse> getOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(tradeService.getOrder(principal, id));
    }
}
