package com.zx.reader.progress;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 阅读进度延迟合并落库 MQ：
 * <pre>
 * reader.progress.delayed (x-delayed-message)
 *   --reader.progress.flush--> reader.progress.flush
 * </pre>
 * Broker 需启用 {@code rabbitmq_delayed_message_exchange}（与购书超时共用插件）。
 */
@Configuration
public class ReaderProgressMqConfig {

    public static final String DELAYED_EXCHANGE = "reader.progress.delayed";
    public static final String FLUSH_QUEUE = "reader.progress.flush";
    public static final String FLUSH_ROUTING_KEY = "reader.progress.flush";

    @Bean
    CustomExchange readerProgressDelayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(DELAYED_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    Queue readerProgressFlushQueue() {
        return QueueBuilder.durable(FLUSH_QUEUE).build();
    }

    @Bean
    Binding readerProgressFlushBinding(Queue readerProgressFlushQueue,
                                       CustomExchange readerProgressDelayedExchange) {
        return BindingBuilder.bind(readerProgressFlushQueue)
                .to(readerProgressDelayedExchange)
                .with(FLUSH_ROUTING_KEY)
                .noargs();
    }
}
