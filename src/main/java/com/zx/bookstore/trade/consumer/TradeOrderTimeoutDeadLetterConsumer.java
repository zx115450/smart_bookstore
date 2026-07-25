package com.zx.bookstore.trade.consumer;

import com.rabbitmq.client.Channel;
import com.zx.bookstore.trade.config.TradeMqConfig;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.service.TradeOrderTimeoutFailService;
import com.zx.bookstore.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 购书超时取消死信消费者：主队列失败后的兜底关单。
 * <p>
 * 仍失败时写入 {@code trade_order_timeout_fail} 并 Ack，避免消息丢弃后无迹可查；
 * 仅当落库也失败时才 Nack 丢弃（并打错误日志）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderTimeoutDeadLetterConsumer {

    private final TradeService tradeService;
    private final TradeOrderTimeoutFailService timeoutFailService;

    @RabbitListener(queues = TradeMqConfig.TRADE_ORDER_TIMEOUT_DLQ)
    public void consume(TradeOrderTimeoutMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            tradeService.cancelOnTimeout(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("reconcile trade order timeout dead letter failed, message={} → persist fail log",
                    message, e);
            try {
                timeoutFailService.recordFailure(message, e);
                channel.basicAck(deliveryTag, false);
            } catch (Exception persistEx) {
                log.error("persist trade timeout fail log also failed, message={} → discard", message, persistEx);
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
