package com.zx.config.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地同步重试入口（仅 {@link MqRetryStrategy#LOCAL}）。
 * QUEUE 策略下只执行一次，延迟重试交给 {@link MqQueueRetrySupport}。
 */
@Slf4j
@Component
public class MqRetrySupport {

    private final BookstoreMqRetryProperties properties;
    private final RetryTemplate mqRetryTemplate;

    public MqRetrySupport(BookstoreMqRetryProperties properties,
                          @Qualifier(MqRetryConfiguration.MQ_RETRY_TEMPLATE) RetryTemplate mqRetryTemplate) {
        this.properties = properties;
        this.mqRetryTemplate = mqRetryTemplate;
    }

    public void execute(String action, Runnable task) {
        boolean useLocal = properties.isEnabled()
                && properties.getStrategy() == MqRetryStrategy.LOCAL
                && properties.getMaxAttempts() > 1;
        if (!useLocal) {
            task.run();
            return;
        }

        AtomicInteger attempts = new AtomicInteger();
        mqRetryTemplate.invoke(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt > 1) {
                log.warn("mq local retry, action={}, attempt={}/{}",
                        action, attempt, properties.getMaxAttempts());
            }
            task.run();
        });
    }
}
