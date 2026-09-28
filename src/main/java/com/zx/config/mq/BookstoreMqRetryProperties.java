package com.zx.config.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ 消费失败重试：支持本地同步重试或 Broker retry 队列延迟重试。
 * <p>
 * 默认 {@link MqRetryStrategy#QUEUE}：等待在 MQ，避免消费线程 sleep 打满。
 */
@ConfigurationProperties(prefix = "bookstore.mq.retry")
public class BookstoreMqRetryProperties {

    /** 总开关；关闭后失败直接走 DLQ（主队列 Nack）。 */
    private boolean enabled = true;

    /** 重试策略，默认 queue。 */
    private MqRetryStrategy strategy = MqRetryStrategy.QUEUE;

    /**
     * 含首次在内的最大尝试次数，建议 3～5。
     * <ul>
     *   <li>LOCAL：映射为 Spring RetryPolicy.maxRetries = maxAttempts - 1</li>
     *   <li>QUEUE：header {@code x-bookstore-retry-count} 从 0 起，耗尽后 Nack → DLQ</li>
     * </ul>
     */
    private int maxAttempts = 3;

    /** 首次进入 retry 队列的等待（毫秒）/ 本地首次退避。 */
    private long initialIntervalMs = 1000L;

    /** 退避倍数。 */
    private double multiplier = 2.0;

    /** 单次等待上限（毫秒）。 */
    private long maxIntervalMs = 10_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MqRetryStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(MqRetryStrategy strategy) {
        this.strategy = strategy;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialIntervalMs() {
        return initialIntervalMs;
    }

    public void setInitialIntervalMs(long initialIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }

    /** 第 {@code retryCount} 次进入 retry 队列时的延迟（retryCount 从 1 起）。 */
    public long resolveDelayMs(int retryCount) {
        int safe = Math.max(1, retryCount);
        double multiplier = this.multiplier <= 0 ? 1.0 : this.multiplier;
        double raw = initialIntervalMs * Math.pow(multiplier, safe - 1);
        long capped = (long) Math.min(raw, Math.max(0L, maxIntervalMs));
        return Math.max(0L, capped);
    }
}
