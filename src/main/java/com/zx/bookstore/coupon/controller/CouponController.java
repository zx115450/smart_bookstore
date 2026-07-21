package com.zx.bookstore.coupon.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.coupon.dto.AvailableCouponResponse;
import com.zx.bookstore.coupon.dto.UserCouponResponse;
import com.zx.bookstore.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/mine")
    public ApiResponse<List<UserCouponResponse>> listMine(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(couponService.listMine(principal.userId(), status));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/available")
    public ApiResponse<List<AvailableCouponResponse>> listAvailable(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam BigDecimal orderAmount
    ) {
        return ApiResponse.ok(couponService.listAvailable(principal.userId(), orderAmount));
    }
}
