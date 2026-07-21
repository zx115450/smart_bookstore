package com.zx.bookstore.coupon.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class CouponException extends BusinessException {

    public CouponException(int code, String message) {
        super(code, message);
    }

    public static CouponException notFound() {
        return new CouponException(ErrorCode.COUPON_NOT_FOUND, "优惠券不存在");
    }

    public static CouponException notUsable() {
        return new CouponException(ErrorCode.COUPON_NOT_USABLE, "优惠券不可用或已过期");
    }

    public static CouponException thresholdNotMet() {
        return new CouponException(ErrorCode.COUPON_THRESHOLD_NOT_MET, "未达到优惠券使用门槛");
    }

    public static CouponException templateNotFound() {
        return new CouponException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND, "优惠券模板不存在");
    }

    public static CouponException unsupportedCouponType() {
        return new CouponException(ErrorCode.COUPON_UNSUPPORTED_TYPE, "不支持的优惠券类型");
    }

    public static CouponException exhausted() {
        return new CouponException(ErrorCode.COUPON_EXHAUSTED, "优惠券已发完");
    }
}
