package com.zx.config.mq;

/** MQ 消费失败后的重试策略。 */
public enum MqRetryStrategy {

    /**
     * 消费线程内同步退避重试（Spring {@code RetryTemplate}）。
     * 实现简单，失败风暴时会占用消费线程。
     */
    LOCAL,

    /**
     * 失败消息发到 Broker 上的 retry 队列（消息 TTL），到期经 DLX 回到主队列。
     * 等待在 Broker，不占用本地消费线程；推荐生产默认。
     */
    QUEUE
}
