package com.zx.reader.agent;

import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyAgentRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private StudyAgentRateLimiter limiter;

    @BeforeEach
    void setUp() {
        ReaderProperties props = new ReaderProperties();
        props.getAgent().setRateLimitPerUserHourly(2);
        when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new StudyAgentRateLimiter(redis, props);
    }

    @Test
    void shouldAllowUnderLimit() {
        when(valueOps.increment(anyString())).thenReturn(1L);
        assertThatCode(() -> limiter.checkAndIncrement(1L)).doesNotThrowAnyException();
        verify(redis).expire(anyString(), eq(Duration.ofHours(2)));
    }

    @Test
    void shouldRejectOverLimit() {
        when(valueOps.increment(anyString())).thenReturn(3L);
        assertThatThrownBy(() -> limiter.checkAndIncrement(1L))
                .isInstanceOf(ReaderException.class);
    }
}
