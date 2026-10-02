package com.zx.reader.agent;

import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Study Agent 限流：每用户每小时 {@code rate-limit-per-user-hourly} 次，超限 5301。
 */
@Component
@RequiredArgsConstructor
public class StudyAgentRateLimiter {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final StringRedisTemplate redis;
    private final ReaderProperties readerProperties;

    public void checkAndIncrement(Long userId) {
        if (userId == null) {
            throw ReaderException.agentRateLimited();
        }
        int limit = Math.max(1, readerProperties.getAgent().getRateLimitPerUserHourly());
        String bucket = LocalDateTime.now().format(HOUR);
        String key = "reader:agent:rate:" + userId + ":" + bucket;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofHours(2));
        }
        if (count != null && count > limit) {
            throw ReaderException.agentRateLimited();
        }
    }
}
