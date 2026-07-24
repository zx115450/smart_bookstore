package com.zx.bookstore.seckill.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

/**
 * 秒杀业务异常。
 */
public class SeckillException extends BusinessException {

    public SeckillException(int code, String message) {
        super(code, message);
    }

    /** 已参与过该活动 */
    public static SeckillException alreadyParticipated() {
        return new SeckillException(ErrorCode.SECKILL_ALREADY_PARTICIPATED, "您已参与过该活动");
    }

    /** 库存不足 */
    public static SeckillException soldOut() {
        return new SeckillException(ErrorCode.SECKILL_SOLD_OUT, "已售罄");
    }

    public static SeckillException activityNotInWindow() {
        return new SeckillException(ErrorCode.SECKILL_NOT_IN_WINDOW, "活动未开始或已结束");
    }

    public static SeckillException activityNotFound() {
        return new SeckillException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND, "活动不存在或已下架");
    }

    public static SeckillException processing() {
        return new SeckillException(ErrorCode.SECKILL_PROCESSING, "排队中，请稍后查询结果");
    }

    public static SeckillException configError(String message) {
        return new SeckillException(ErrorCode.SECKILL_CONFIG_ERROR, message);
    }

    public static SeckillException systemBusy() {
        return new SeckillException(ErrorCode.SECKILL_SYSTEM_BUSY, "系统繁忙，请稍后重试");
    }

    public static SeckillException notParticipated() {
        return new SeckillException(ErrorCode.SECKILL_NOT_PARTICIPATED, "尚未参与该活动");
    }

    /** 入口令牌桶未领到令牌（活动或用户维度限流）。 */
    public static SeckillException rateLimited() {
        return new SeckillException(ErrorCode.SECKILL_RATE_LIMITED, "活动太火爆，请稍后再试");
    }
}
