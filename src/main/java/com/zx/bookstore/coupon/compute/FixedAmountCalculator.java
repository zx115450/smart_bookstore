package com.zx.bookstore.coupon.compute;

import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.enums.CouponType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedAmountCalculator implements CouponDiscountCalculator {

    @Override
    public boolean supports(String couponType) {
        return CouponType.from(couponType) == CouponType.FIXED;
    }

    @Override
    public BigDecimal calculate(CouponTemplate template, BigDecimal totalAmount) {
        BigDecimal discount = template.getDiscountAmount() == null ? BigDecimal.ZERO : template.getDiscountAmount();
        return discount.min(totalAmount);
    }
}
