package com.zx.bookstore.borrow.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.borrow.dto.BorrowOrderResponse;
import com.zx.bookstore.borrow.service.BorrowService;
import com.zx.bookstore.catalog.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/borrow")
@RequiredArgsConstructor
public class BorrowAdminController {

    private final BorrowService borrowService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ApiResponse<PageResult<BorrowOrderResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(borrowService.listAllOrders(status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/confirm")
    public ApiResponse<BorrowOrderResponse> confirm(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(borrowService.confirm(id, principal.userId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/reject")
    public ApiResponse<BorrowOrderResponse> reject(@PathVariable Long id) {
        return ApiResponse.ok(borrowService.reject(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/orders/{id}/return")
    public ApiResponse<BorrowOrderResponse> returnBook(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(borrowService.adminReturn(id, principal.userId()));
    }
}
