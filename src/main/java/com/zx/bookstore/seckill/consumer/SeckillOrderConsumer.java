package com.zx.bookstore.seckill.consumer;

import com.rabbitmq.client.Channel;
import com.zx.bookstore.seckill.config.RabbitMqConfig;
import com.zx.bookstore.seckill.dto.SeckillOrderMessage;
import com.zx.bookstore.seckill.service.SeckillService;
import com.zx.config.mq.MqQueueRetrySupport;
import com.zx.config.mq.MqRetrySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单 MQ 消费者：手动 ACK。
 * <p>
 * 默认 QUEUE 策略：失败则投递 {@code seckill.order.retry}（TTL）后 Ack，到期回主队列；
 * 超次或不可重试则 Nack 进 DLQ。LOCAL 策略仍走消费线程内 {@link MqRetrySupport}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillService seckillService;
    private final MqRetrySupport mqRetrySupport;
    private final MqQueueRetrySupport mqQueueRetrySupport;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_QUEUE)
    public void consume(SeckillOrderMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = MqQueueRetrySupport.RETRY_COUNT_HEADER, required = false) Integer retryCount)
            throws IOException {
        try {
            mqRetrySupport.execute("seckill.order", () -> seckillService.processSeckillOrder(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            if (mqQueueRetrySupport.tryScheduleRetry(
                    "seckill.order",
                    message,
                    retryCount,
                    RabbitMqConfig.SECKILL_ORDER_RETRY_QUEUE,
                    e)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.error("consume seckill order failed → dead letter, message={}, retryCount={}",
                    message, retryCount, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
