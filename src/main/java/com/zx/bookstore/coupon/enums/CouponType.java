package com.zx.bookstore.coupon.enums;

public enum CouponType {
    FIXED,
    PERCENT;

    public static CouponType from(String type) {
        if (type == null || type.isBlank()) {
            return FIXED;
        }
        return CouponType.valueOf(type);
    }
}
