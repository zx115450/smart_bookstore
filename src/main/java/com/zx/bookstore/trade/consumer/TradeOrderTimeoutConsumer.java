package com.zx.bookstore.trade.consumer;

import com.rabbitmq.client.Channel;
import com.zx.bookstore.trade.config.TradeMqConfig;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 购书超时取消消费者：延迟到期后若订单仍为 PENDING_PAY 则自动关单。
 * 失败 NACK 且不 requeue，进入死信队列 {@code trade.order.timeout.dlq}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutConsumer {

    private final TradeService tradeService;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_QUEUE)
    public void consume(TradeOrderTimeoutMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            tradeService.cancelOnTimeout(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("consume trade order timeout failed, message={} → dead letter", message, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
