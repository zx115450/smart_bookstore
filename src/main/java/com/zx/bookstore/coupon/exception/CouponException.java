package com.zx.bookstore.coupon.exception;

public class CouponException extends RuntimeException {

    private final int code;

    public CouponException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CouponException notFound() {
        return new CouponException(4101, "优惠券不存在");
    }

    public static CouponException notUsable() {
        return new CouponException(4102, "优惠券不可用或已过期");
    }

    public static CouponException thresholdNotMet() {
        return new CouponException(4103, "未达到优惠券使用门槛");
    }

    public static CouponException templateNotFound() {
        return new CouponException(4104, "优惠券模板不存在");
    }
}
