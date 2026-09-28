package com.zx.bookstore.seckill.consumer;

import com.rabbitmq.client.Channel;
import com.zx.bookstore.seckill.config.RabbitMqConfig;
import com.zx.bookstore.seckill.dto.SeckillOrderMessage;
import com.zx.bookstore.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀死信消费者：主队列（含 retry 耗尽）失败后的兜底对账。
 * 不重投主队列；对账失败也不 requeue，依赖日志告警人工介入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillDeadLetterConsumer {

    private final SeckillService seckillService;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_DLQ)
    public void consume(SeckillOrderMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            seckillService.reconcileDeadLetter(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("reconcile seckill dead letter failed, message={}", message, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
