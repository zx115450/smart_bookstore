package com.zx.config.mq;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqRetrySupportTest {

    @Test
    void retriesTransientFailuresUntilSuccess() {
        BookstoreMqRetryProperties props = props(true, 3, 1);
        MqRetrySupport support = new MqRetrySupport(props, new MqRetryConfiguration().mqRetryTemplate(props));

        AtomicInteger attempts = new AtomicInteger();
        support.execute("unit", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
        });

        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryBusinessException() {
        BookstoreMqRetryProperties props = props(true, 5, 1);
        MqRetrySupport support = new MqRetrySupport(props, new MqRetryConfiguration().mqRetryTemplate(props));

        AtomicInteger attempts = new AtomicInteger();
        assertThrows(BusinessException.class, () -> support.execute("unit", () -> {
            attempts.incrementAndGet();
            throw new BusinessException(ErrorCode.SECKILL_CONFIG_ERROR, "bad config");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void doesNotRetryIllegalArgumentException() {
        BookstoreMqRetryProperties props = props(true, 5, 1);
        MqRetrySupport support = new MqRetrySupport(props, new MqRetryConfiguration().mqRetryTemplate(props));

        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> support.execute("unit", () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("bad message");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void disabledRunsOnce() {
        BookstoreMqRetryProperties props = props(false, 5, 1);
        MqRetrySupport support = new MqRetrySupport(props, new MqRetryConfiguration().mqRetryTemplate(props));

        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> support.execute("unit", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void exhaustsThenRethrowsLastError() {
        BookstoreMqRetryProperties props = props(true, 3, 1);
        MqRetrySupport support = new MqRetrySupport(props, new MqRetryConfiguration().mqRetryTemplate(props));

        AtomicInteger attempts = new AtomicInteger();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> support.execute("unit", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("still down");
                }));
        assertEquals(3, attempts.get());
        assertEquals("still down", ex.getMessage());
    }

    private static BookstoreMqRetryProperties props(boolean enabled, int maxAttempts, long intervalMs) {
        BookstoreMqRetryProperties props = new BookstoreMqRetryProperties();
        props.setEnabled(enabled);
        props.setStrategy(MqRetryStrategy.LOCAL);
        props.setMaxAttempts(maxAttempts);
        props.setInitialIntervalMs(intervalMs);
        props.setMultiplier(1.0);
        props.setMaxIntervalMs(intervalMs);
        return props;
    }
}
