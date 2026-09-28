package com.zx.config.mq;

import com.zx.common.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

/**
 * 构建 MQ 消费专用 {@link RetryTemplate}（Spring Framework 7 内置）：
 * 指数退避；业务异常 / 参数异常不重试。
 */
@Configuration
@EnableConfigurationProperties(BookstoreMqRetryProperties.class)
public class MqRetryConfiguration {

    public static final String MQ_RETRY_TEMPLATE = "mqRetryTemplate";

    @Bean(name = MQ_RETRY_TEMPLATE)
    RetryTemplate mqRetryTemplate(BookstoreMqRetryProperties props) {
        // Spring 7：maxRetries = 首次失败后的重试次数；总执行次数 = 1 + maxRetries
        int maxRetries = Math.max(0, props.getMaxAttempts() - 1);
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(Duration.ofMillis(Math.max(0L, props.getInitialIntervalMs())))
                .multiplier(props.getMultiplier() <= 0 ? 1.0 : props.getMultiplier())
                .maxDelay(Duration.ofMillis(Math.max(0L, props.getMaxIntervalMs())))
                .excludes(BusinessException.class, IllegalArgumentException.class)
                .build();
        return new RetryTemplate(policy);
    }
}
