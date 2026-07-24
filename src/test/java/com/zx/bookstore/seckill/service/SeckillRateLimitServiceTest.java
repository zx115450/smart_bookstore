package com.zx.bookstore.seckill.service;

import com.zx.bookstore.seckill.config.SeckillRateLimitProperties;
import com.zx.bookstore.seckill.exception.SeckillException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 令牌桶限流服务单测：开关、活动桶拒绝、用户桶拒绝。
 */
@ExtendWith(MockitoExtension.class)
class SeckillRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;

    private SeckillRateLimitProperties properties;
    private SeckillRateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new SeckillRateLimitProperties();
        properties.setEnabled(true);
        properties.setActivityCapacity(10);
        properties.setActivityRefillPerSecond(10);
        properties.setUserCapacity(2);
        properties.setUserRefillPerSecond(1);
        properties.setKeyTtlSeconds(3600);

        service = new SeckillRateLimitService(redis, properties);
        service.initScript();
    }

    @Test
    void assertAllowed_shouldSkip_whenDisabled() {
        properties.setEnabled(false);
        assertDoesNotThrow(() -> service.assertAllowed(1L, 2L));
        verifyNoInteractions(redis);
    }

    @Test
    void assertAllowed_shouldPass_whenBothBucketsAllow() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L);

        assertDoesNotThrow(() -> service.assertAllowed(10L, 1L));
        verify(redis, times(2)).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void assertAllowed_shouldThrow_whenActivityBucketDenies() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(0L);

        SeckillException ex = assertThrows(SeckillException.class,
                () -> service.assertAllowed(10L, 1L));
        assertTrue(ex.getMessage().contains("火爆"));
        verify(redis, times(1)).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void assertAllowed_shouldThrow_whenUserBucketDenies() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L, 0L);

        SeckillException ex = assertThrows(SeckillException.class,
                () -> service.assertAllowed(10L, 1L));
        assertTrue(ex.getMessage().contains("火爆"));
        verify(redis, times(2)).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void tryAcquire_shouldReturnFalse_whenCapacityInvalid() {
        assertFalse(service.tryAcquire("k", 0, 1.0, System.currentTimeMillis()));
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
    }
}
