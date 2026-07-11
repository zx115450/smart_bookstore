package com.zx.bookstore.coupon.compute;

import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.enums.CouponType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PercentDiscountCalculator implements CouponDiscountCalculator {

    @Override
    public boolean supports(String couponType) {
        return CouponType.from(couponType) == CouponType.PERCENT;
    }

    @Override
    public BigDecimal calculate(CouponTemplate template, BigDecimal totalAmount) {
        BigDecimal discountAmount = template.getDiscountAmount() == null ? BigDecimal.ZERO : template.getDiscountAmount();
        BigDecimal rate = discountAmount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return totalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
