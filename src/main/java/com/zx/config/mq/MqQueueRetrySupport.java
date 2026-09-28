package com.zx.config.mq;

import com.zx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Broker 侧延迟重试：把失败消息发到 retry 队列（per-message TTL），
 * 到期后由队列 DLX 回到主队列；超次或不可重试异常返回 false，由调用方 Nack → DLQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqQueueRetrySupport {

    /** 已进入 retry 队列的次数；首次投递为 0 / 缺失。 */
    public static final String RETRY_COUNT_HEADER = "x-bookstore-retry-count";

    private final BookstoreMqRetryProperties properties;
    private final RabbitTemplate rabbitTemplate;

    /**
     * @param currentRetryCount 消息头中的当前次数，可为 null
     * @param retryQueueName    无消费者的等待队列名（默认交换机按队列名路由）
     * @return true 表示已投递 retry 并应 Ack 原消息；false 表示应 Nack 进 DLQ
     */
    public boolean tryScheduleRetry(String action,
                                    Object payload,
                                    Integer currentRetryCount,
                                    String retryQueueName,
                                    Throwable error) {
        if (!properties.isEnabled() || properties.getStrategy() != MqRetryStrategy.QUEUE) {
            return false;
        }
        if (isNonRetryable(error)) {
            log.warn("mq queue retry skipped (non-retryable), action={}, cause={}",
                    action, error.toString());
            return false;
        }

        int count = currentRetryCount == null ? 0 : Math.max(0, currentRetryCount);
        // 当前这次已算一次尝试；若再失败后总次数达到上限则进 DLQ
        if (count + 1 >= Math.max(1, properties.getMaxAttempts())) {
            log.warn("mq queue retry exhausted, action={}, retryCount={}, maxAttempts={}",
                    action, count, properties.getMaxAttempts());
            return false;
        }

        int nextCount = count + 1;
        long delayMs = properties.resolveDelayMs(nextCount);
        rabbitTemplate.convertAndSend("", retryQueueName, payload, message -> {
            message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextCount);
            message.getMessageProperties().setExpiration(String.valueOf(delayMs));
            return message;
        });
        log.warn("mq queue retry scheduled, action={}, retryCount={}/{}, delayMs={}, queue={}",
                action, nextCount, properties.getMaxAttempts(), delayMs, retryQueueName);
        return true;
    }

    static boolean isNonRetryable(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof BusinessException || cursor instanceof IllegalArgumentException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
