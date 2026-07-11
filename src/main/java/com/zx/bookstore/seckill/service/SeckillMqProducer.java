package com.zx.bookstore.seckill.service;

import com.zx.bookstore.seckill.config.RabbitMqConfig;
import com.zx.bookstore.seckill.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** 秒杀异步发券消息生产者，抢券 Lua 成功后调用。 */
@Service
@RequiredArgsConstructor
public class SeckillMqProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishSeckillOrder(SeckillOrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                message
        );
    }
}
