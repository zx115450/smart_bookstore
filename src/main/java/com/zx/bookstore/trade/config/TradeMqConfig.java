package com.zx.bookstore.trade.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 购书超时取消 MQ 拓扑（RabbitMQ 延迟消息插件）：
 * <pre>
 * trade.delayed (x-delayed-message, x-delay header)
 *       │ routing: trade.order.delay
 *       ▼
 * trade.order.timeout ──Nack(requeue=false)──► trade.timeout.dlx ──► trade.order.timeout.dlq
 *       DLQ 仍失败 ──► trade_order_timeout_fail 落库后 Ack（落库失败才丢弃）
 * </pre>
 * 需在 Broker 启用 {@code rabbitmq_delayed_message_exchange} 插件。
 */
@Configuration
public class TradeMqConfig {

    public static final String TRADE_DELAYED_EXCHANGE = "trade.delayed";
    public static final String TRADE_ORDER_TIMEOUT_QUEUE = "trade.order.timeout";
    public static final String TRADE_ORDER_TIMEOUT_ROUTING_KEY = "trade.order.delay";

    public static final String TRADE_TIMEOUT_DLX = "trade.timeout.dlx";
    public static final String TRADE_ORDER_TIMEOUT_DLQ = "trade.order.timeout.dlq";
    public static final String TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY = "trade.order.timeout.dlq";



    @Bean
    CustomExchange tradeDelayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(TRADE_DELAYED_EXCHANGE, "x-delayed-message", true, false, args);
    }
    @Bean
    Queue tradeOrderTimeoutQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", TRADE_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     *死信交换机和死信队列的声明
     * @return
     */
    @Bean
    Queue tradeOrderTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_DLQ).build();
    }
    @Bean
    DirectExchange tradeTimeoutDeadLetterExchange() {
        return new DirectExchange(TRADE_TIMEOUT_DLX, true, false);
    }

    /**
     * binding的声明
     * @param tradeOrderTimeoutQueue
     * @param tradeDelayedExchange
     * @return
     */
    @Bean
    Binding tradeOrderTimeoutBinding(Queue tradeOrderTimeoutQueue, CustomExchange tradeDelayedExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutQueue)
                .to(tradeDelayedExchange)
                .with(TRADE_ORDER_TIMEOUT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    Binding tradeOrderTimeoutDeadLetterBinding(Queue tradeOrderTimeoutDeadLetterQueue,
                                               DirectExchange tradeTimeoutDeadLetterExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutDeadLetterQueue)
                .to(tradeTimeoutDeadLetterExchange)
                .with(TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY);
    }
}
