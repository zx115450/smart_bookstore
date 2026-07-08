package com.zx.bookstore.coupon.enums;

public enum UserCouponStatus {
    UNUSED,
    USED,
    EXPIRED;

    public static UserCouponStatus from(String status) {
        return UserCouponStatus.valueOf(status);
    }
}
