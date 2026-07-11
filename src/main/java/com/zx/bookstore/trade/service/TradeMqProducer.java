package com.zx.bookstore.trade.service;

import com.zx.bookstore.trade.config.BookstoreTradeProperties;
import com.zx.bookstore.trade.config.TradeMqConfig;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** 购书订单超时取消延迟消息生产者（x-delayed-message 插件）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeMqProducer {

    private final RabbitTemplate rabbitTemplate;
    private final BookstoreTradeProperties tradeProperties;

    public void publishOrderTimeout(TradeOrderTimeoutMessage message) {
        long delayMs = tradeProperties.resolveCancelDelayMs();
        rabbitTemplate.convertAndSend(
                TradeMqConfig.TRADE_DELAYED_EXCHANGE,
                TradeMqConfig.TRADE_ORDER_TIMEOUT_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setHeader("x-delay", delayMs);
                    return msg;
                }
        );
        log.info("published trade order timeout message, orderId={}, orderNo={}, delayMs={}",
                message.getOrderId(), message.getOrderNo(), delayMs);
    }
}
