package com.zx.bookstore.seckill.enums;

/** 秒杀订单状态：MQ 消费中为 PROCESSING，发券完成后 SUCCESS。 */
public enum SeckillOrderStatus {
    PROCESSING,
    SUCCESS,
    FAILED
}
