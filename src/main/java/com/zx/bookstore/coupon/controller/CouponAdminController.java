package com.zx.bookstore.coupon.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.coupon.dto.CreateCouponTemplateRequest;
import com.zx.bookstore.coupon.dto.CouponTemplateResponse;
import com.zx.bookstore.coupon.dto.UpdateCouponTemplateRequest;
import com.zx.bookstore.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/coupon-templates")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponService couponService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<PageResult<CouponTemplateResponse>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(couponService.listTemplatesAdmin(status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<CouponTemplateResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(couponService.getTemplateAdmin(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<CouponTemplateResponse> create(@RequestBody CreateCouponTemplateRequest req) {
        return ApiResponse.ok(couponService.createTemplate(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<CouponTemplateResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateCouponTemplateRequest req
    ) {
        return ApiResponse.ok(couponService.updateTemplate(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        couponService.disableTemplate(id);
        return ApiResponse.ok(null);
    }
}
