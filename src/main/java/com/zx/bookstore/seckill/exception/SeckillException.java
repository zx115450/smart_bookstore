package com.zx.bookstore.seckill.exception;

/**
 * 秒杀业务异常，错误码段 4251～4257（与购书交易 4201 段区分）。
 */
public class SeckillException extends RuntimeException {

    private final int code;

    public SeckillException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 4251：Lua 返回 2 或 DB 唯一约束冲突 */
    public static SeckillException alreadyParticipated() {
        return new SeckillException(4251, "您已参与过该活动");
    }

    /** 4252：Lua 返回 0，库存不足 */
    public static SeckillException soldOut() {
        return new SeckillException(4252, "已售罄");
    }

    public static SeckillException activityNotInWindow() {
        return new SeckillException(4253, "活动未开始或已结束");
    }

    public static SeckillException activityNotFound() {
        return new SeckillException(4254, "活动不存在或已下架");
    }

    public static SeckillException processing() {
        return new SeckillException(4255, "排队中，请稍后查询结果");
    }

    public static SeckillException configError(String message) {
        return new SeckillException(4256, message);
    }
}
