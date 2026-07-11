package com.zx.bookstore.coupon.compute;

import com.zx.bookstore.coupon.exception.CouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponDiscountCalculatorFactory {

    private final List<CouponDiscountCalculator> calculators;

    public CouponDiscountCalculator getCalculator(String couponType) {
        return calculators.stream()
                .filter(c -> c.supports(couponType))
                .findFirst()
                .orElseThrow(CouponException::unsupportedCouponType);
    }
}
