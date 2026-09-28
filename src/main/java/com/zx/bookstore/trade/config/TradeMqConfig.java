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
 * trade.delayed (x-delayed-message) ──trade.order.delay──► trade.order.timeout
 *       │ 失败且未超次：发到 trade.order.timeout.retry（TTL）后 Ack
 *       │                 │ 到期 DLX
 *       │                 └── trade.timeout.reentry ──► trade.order.timeout
 *       │ 超次 / 不可重试：Nack(requeue=false)
 *       ▼
 * trade.timeout.dlx ──► trade.order.timeout.dlq
 *       DLQ 仍失败 ──► trade_order_timeout_fail 落库后 Ack
 * </pre>
 * 需在 Broker 启用 {@code rabbitmq_delayed_message_exchange} 插件。
 */
@Configuration
public class TradeMqConfig {

    public static final String TRADE_DELAYED_EXCHANGE = "trade.delayed";
    public static final String TRADE_ORDER_TIMEOUT_QUEUE = "trade.order.timeout";
    public static final String TRADE_ORDER_TIMEOUT_ROUTING_KEY = "trade.order.delay";

    /** retry 队列 TTL 到期后回到主队列用（避免再走业务延迟的 x-delay）。 */
    public static final String TRADE_TIMEOUT_REENTRY_EXCHANGE = "trade.timeout.reentry";
    public static final String TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY = "trade.order.timeout";

    public static final String TRADE_ORDER_TIMEOUT_RETRY_QUEUE = "trade.order.timeout.retry";

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
    DirectExchange tradeTimeoutReentryExchange() {
        return new DirectExchange(TRADE_TIMEOUT_REENTRY_EXCHANGE, true, false);
    }

    @Bean
    Queue tradeOrderTimeoutQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", TRADE_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue tradeOrderTimeoutRetryQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_RETRY_QUEUE)
                .withArgument("x-dead-letter-exchange", TRADE_TIMEOUT_REENTRY_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue tradeOrderTimeoutDeadLetterQueue() {
        return QueueBuilder.durable(TRADE_ORDER_TIMEOUT_DLQ).build();
    }

    @Bean
    DirectExchange tradeTimeoutDeadLetterExchange() {
        return new DirectExchange(TRADE_TIMEOUT_DLX, true, false);
    }

    @Bean
    Binding tradeOrderTimeoutBinding(Queue tradeOrderTimeoutQueue, CustomExchange tradeDelayedExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutQueue)
                .to(tradeDelayedExchange)
                .with(TRADE_ORDER_TIMEOUT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    Binding tradeOrderTimeoutReentryBinding(Queue tradeOrderTimeoutQueue,
                                            DirectExchange tradeTimeoutReentryExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutQueue)
                .to(tradeTimeoutReentryExchange)
                .with(TRADE_ORDER_TIMEOUT_REENTRY_ROUTING_KEY);
    }

    @Bean
    Binding tradeOrderTimeoutDeadLetterBinding(Queue tradeOrderTimeoutDeadLetterQueue,
                                               DirectExchange tradeTimeoutDeadLetterExchange) {
        return BindingBuilder.bind(tradeOrderTimeoutDeadLetterQueue)
                .to(tradeTimeoutDeadLetterExchange)
                .with(TRADE_ORDER_TIMEOUT_DLQ_ROUTING_KEY);
    }
}
