package com.zx.bookstore.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RabbitMQ seckill.order 队列消息体，Consumer 据此落库发券。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {

    private Long userId;
    private Long activityId;
    private String idempotencyKey;
}
