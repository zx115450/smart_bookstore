package com.zx.bookstore.seckill.service;

import com.zx.bookstore.seckill.config.SeckillRateLimitProperties;
import com.zx.bookstore.seckill.exception.SeckillException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀 grab 令牌桶限流：活动维度 + 用户维度，均由 Redis Lua 原子补令牌并尝试领取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillRateLimitService {

    private static final String ACTIVITY_BUCKET_PREFIX = "seckill:bucket:act:";

    private final StringRedisTemplate redis;
    private final SeckillRateLimitProperties properties;
    private DefaultRedisScript<Long> tokenBucketScript;

    @PostConstruct
    void initScript() {
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setResultType(Long.class);
        tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/seckill_token_bucket.lua")));
    }

    /**
     * 通过限流则返回；否则抛出 {@link SeckillException#rateLimited()}。
     * 关闭限流（{@code enabled=false}）时直接放行。
     */
    public void assertAllowed(Long activityId, Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        if (activityId == null || userId == null) {
            throw SeckillException.rateLimited();
        }
        long nowMs = System.currentTimeMillis();
        if (!tryAcquire(activityBucketKey(activityId),
                properties.getActivityCapacity(),
                properties.getActivityRefillPerSecond(),
                nowMs)) {
            log.info("seckill rate limited by activity bucket, activityId={}", activityId);
            throw SeckillException.rateLimited();
        }
        if (!tryAcquire(userBucketKey(activityId, userId),
                properties.getUserCapacity(),
                properties.getUserRefillPerSecond(),
                nowMs)) {
            log.info("seckill rate limited by user bucket, activityId={}, userId={}", activityId, userId);
            throw SeckillException.rateLimited();
        }
    }

    /**
     * 对指定桶尝试领取 1 个令牌。
     *
     * @return true=成功，false=桶空
     */
    boolean tryAcquire(String bucketKey, int capacity, double refillPerSecond, long nowMs) {
        if (capacity <= 0) {
            return false;
        }
        Long result = redis.execute(
                tokenBucketScript,
                List.of(bucketKey),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(nowMs),
                "1",
                String.valueOf(Math.max(60L, properties.getKeyTtlSeconds()))
        );
        return result != null && result == 1L;
    }

    private String activityBucketKey(Long activityId) {
        return ACTIVITY_BUCKET_PREFIX + activityId;
    }

    private String userBucketKey(Long activityId, Long userId) {
        return ACTIVITY_BUCKET_PREFIX + activityId + ":user:" + userId;
    }
}
