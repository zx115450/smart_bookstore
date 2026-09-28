package com.zx.config.mq;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MqQueueRetrySupportTest {

    @Test
    void schedulesRetryWhenUnderMaxAttempts() {
        BookstoreMqRetryProperties props = baseProps();
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MqQueueRetrySupport support = new MqQueueRetrySupport(props, rabbitTemplate);

        boolean scheduled = support.tryScheduleRetry(
                "unit", "payload", 0, "q.retry", new IllegalStateException("down"));

        assertTrue(scheduled);
        verify(rabbitTemplate).convertAndSend(eq(""), eq("q.retry"), eq("payload"), any(MessagePostProcessor.class));
    }

    @Test
    void rejectsWhenExhausted() {
        BookstoreMqRetryProperties props = baseProps();
        props.setMaxAttempts(3);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MqQueueRetrySupport support = new MqQueueRetrySupport(props, rabbitTemplate);

        // count=2 表示已重试两次，当前为第 3 次尝试失败 → 耗尽
        assertFalse(support.tryScheduleRetry(
                "unit", "payload", 2, "q.retry", new IllegalStateException("down")));
        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class));
    }

    @Test
    void rejectsBusinessException() {
        BookstoreMqRetryProperties props = baseProps();
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MqQueueRetrySupport support = new MqQueueRetrySupport(props, rabbitTemplate);

        assertFalse(support.tryScheduleRetry(
                "unit",
                "payload",
                0,
                "q.retry",
                new BusinessException(ErrorCode.SECKILL_CONFIG_ERROR, "bad")));
        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class));
    }

    @Test
    void resolveDelayUsesExponentialBackoff() {
        BookstoreMqRetryProperties props = baseProps();
        props.setInitialIntervalMs(1000);
        props.setMultiplier(2.0);
        props.setMaxIntervalMs(10_000);

        assertEquals(1000, props.resolveDelayMs(1));
        assertEquals(2000, props.resolveDelayMs(2));
        assertEquals(4000, props.resolveDelayMs(3));
        assertEquals(8000, props.resolveDelayMs(4));
        assertEquals(10_000, props.resolveDelayMs(5));
    }

    private static BookstoreMqRetryProperties baseProps() {
        BookstoreMqRetryProperties props = new BookstoreMqRetryProperties();
        props.setEnabled(true);
        props.setStrategy(MqRetryStrategy.QUEUE);
        props.setMaxAttempts(3);
        props.setInitialIntervalMs(1000);
        props.setMultiplier(2.0);
        props.setMaxIntervalMs(10_000);
        return props;
    }
}
