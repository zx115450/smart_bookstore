package com.zx.bookstore.coupon.compute;

import com.zx.bookstore.coupon.entity.CouponTemplate;

import java.math.BigDecimal;

/**
 * Strategy interface for coupon discount calculation (fixed amount, percent, etc.).
 */
public interface CouponDiscountCalculator {

    boolean supports(String couponType);

    BigDecimal calculate(CouponTemplate template, BigDecimal totalAmount);
}
