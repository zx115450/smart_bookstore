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
 * 秒杀订单 MQ 消费者：手动 ACK，落库 + 发券成功后再确认消息。
 * 失败 NACK 且不 requeue，消息进入死信队列 {@code seckill.order.dlq} 做对账补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillService seckillService;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_QUEUE)
    public void consume(SeckillOrderMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            seckillService.processSeckillOrder(message);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("consume seckill order failed, message={} → dead letter", message, e);
            // requeue=false：不重回主队列，由 Broker 转发到 DLX/DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
