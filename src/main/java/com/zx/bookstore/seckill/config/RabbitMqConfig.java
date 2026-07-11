package com.zx.bookstore.seckill.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：
 * <pre>
 * bookstore.topic ──seckill.order──► seckill.order
 *                                       │ Nack(requeue=false)
 *                                       ▼
 *                                  seckill.dlx ──► seckill.order.dlq
 * </pre>
 * 主队列失败消息进死信，由死信消费者对账补偿 Redis/DB。
 * <p>
 * 注意：若本地已存在无 DLX 参数的 {@code seckill.order}，需先在管理台删除该队列再启动，
 * 否则声明参数不一致会报错。
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "bookstore.topic";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_DLX = "seckill.dlx";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";
    public static final String SECKILL_ORDER_DLQ_ROUTING_KEY = "seckill.order.dlq";

    @Bean
    TopicExchange bookstoreTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** 死信交换机：主队列拒收/Nack 的消息路由到此。 */
    @Bean
    DirectExchange seckillDeadLetterExchange() {
        return new DirectExchange(SECKILL_DLX, true, false);
    }

    @Bean
    Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", SECKILL_DLX)
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue seckillOrderDeadLetterQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DLQ).build();
    }

    @Bean
    Binding seckillOrderBinding(Queue seckillOrderQueue, TopicExchange bookstoreTopicExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(bookstoreTopicExchange)
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    Binding seckillOrderDeadLetterBinding(Queue seckillOrderDeadLetterQueue,
                                          DirectExchange seckillDeadLetterExchange) {
        return BindingBuilder.bind(seckillOrderDeadLetterQueue)
                .to(seckillDeadLetterExchange)
                .with(SECKILL_ORDER_DLQ_ROUTING_KEY);
    }

    @Bean
    JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  JacksonJsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
