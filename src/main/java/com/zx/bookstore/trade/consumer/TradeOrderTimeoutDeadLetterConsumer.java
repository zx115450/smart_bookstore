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

/** 购书超时取消死信消费者：主队列处理失败的兜底重试关单。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutDeadLetterConsumer {

    private final TradeService tradeService;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_DLQ)
    public void consume(TradeOrderTimeoutMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            tradeService.cancelOnTimeout(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("reconcile trade order timeout dead letter failed, message={}", message, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
