package com.zx.bookstore.borrow.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.borrow.dto.BorrowOrderResponse;
import com.zx.bookstore.borrow.dto.CreateBorrowOrderRequest;
import com.zx.bookstore.borrow.service.BorrowService;
import com.zx.bookstore.catalog.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/borrow")
@RequiredArgsConstructor
public class BorrowController {

    private final BorrowService borrowService;

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders")
    public ApiResponse<BorrowOrderResponse> apply(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody CreateBorrowOrderRequest req
    ) {
        return ApiResponse.ok(borrowService.apply(principal, req));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<BorrowOrderResponse> cancel(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(borrowService.cancel(principal, id));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/orders/{id}/return")
    public ApiResponse<BorrowOrderResponse> returnBook(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(borrowService.returnBook(principal, id));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/mine")
    public ApiResponse<PageResult<BorrowOrderResponse>> listMyOrders(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(borrowService.listMyOrders(principal, status, page, size));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/orders/{id}")
    public ApiResponse<BorrowOrderResponse> getOrder(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(borrowService.getOrder(principal, id));
    }
}
