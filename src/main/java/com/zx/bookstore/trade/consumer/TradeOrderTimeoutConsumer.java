package com.zx.bookstore.trade.consumer;

import com.rabbitmq.client.Channel;
import com.zx.bookstore.trade.config.TradeMqConfig;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.service.TradeService;
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
 * 购书超时取消消费者：延迟到期后若仍为 PENDING_PAY 则关单。
 * <p>
 * 默认 QUEUE 策略：失败投递 {@code trade.order.timeout.retry} 后 Ack；超次 Nack → DLQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutConsumer {

    private final TradeService tradeService;
    private final MqRetrySupport mqRetrySupport;
    private final MqQueueRetrySupport mqQueueRetrySupport;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_QUEUE)
    public void consume(TradeOrderTimeoutMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(value = MqQueueRetrySupport.RETRY_COUNT_HEADER, required = false) Integer retryCount)
            throws IOException {
        try {
            mqRetrySupport.execute("trade.order.timeout", () -> tradeService.cancelOnTimeout(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            if (mqQueueRetrySupport.tryScheduleRetry(
                    "trade.order.timeout",
                    message,
                    retryCount,
                    TradeMqConfig.TRADE_ORDER_TIMEOUT_RETRY_QUEUE,
                    e)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.error("consume trade order timeout failed → dead letter, message={}, retryCount={}",
                    message, retryCount, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
