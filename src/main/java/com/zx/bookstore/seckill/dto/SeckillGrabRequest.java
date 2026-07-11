package com.zx.bookstore.seckill.dto;

import lombok.Data;

@Data
public class SeckillGrabRequest {

    /** 幂等键：前端生成 UUID，抢券重试时复用同一 key，防止重复扣库存/重复发 MQ。 */
    private String idempotencyKey;
}
