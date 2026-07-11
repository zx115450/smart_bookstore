package com.zx.bookstore.seckill.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀 Redis 层：库存 Key、已抢用户 Set、Lua 原子抢券。
 * <p>
 * Key 规划：seckill:stock:{activityId}、seckill:users:{activityId}，TTL 至活动结束 + 1 天缓冲。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillRedisService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USERS_KEY_PREFIX = "seckill:users:";

    private final StringRedisTemplate redis;
    private DefaultRedisScript<Long> grabScript;

    @PostConstruct
    void initScript() {
        grabScript = new DefaultRedisScript<>();
        grabScript.setResultType(Long.class);
        grabScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_grab.lua")));
    }

    /** 管理端创建/启用活动时预热库存，清空历史参与用户 Set。 */
    public void warmUpStock(Long activityId, int stock, LocalDateTime endTime) {
        String stockKey = stockKey(activityId);
        String usersKey = usersKey(activityId);
        Duration ttl = ttlUntil(endTime);
        redis.opsForValue().set(stockKey, String.valueOf(stock), ttl);
        redis.delete(usersKey);
        redis.expire(usersKey, ttl);
    }

    /** 执行 Lua 抢券，返回 0/1/2，见 seckill_grab.lua。 */
    public Long grab(Long activityId, Long userId) {
        Long result = redis.execute(
                grabScript,
                List.of(stockKey(activityId), usersKey(activityId)),
                String.valueOf(userId)
        );
        return result == null ? 0L : result;
    }

    /** MQ 发送失败或发券失败时补偿：库存 +1，从用户 Set 移除。 */
    public void rollbackGrab(Long activityId, Long userId) {
        redis.opsForValue().increment(stockKey(activityId));
        redis.opsForSet().remove(usersKey(activityId), String.valueOf(userId));
    }

    public Integer getRemainingStock(Long activityId) {
        String value = redis.opsForValue().get(stockKey(activityId));
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("invalid seckill stock value, activityId={}, value={}", activityId, value);
            return null;
        }
    }

    private Duration ttlUntil(LocalDateTime endTime) {
        LocalDateTime expireAt = endTime.plusDays(1);
        Duration ttl = Duration.between(LocalDateTime.now(), expireAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofDays(1);
        }
        return ttl;
    }

    private String stockKey(Long activityId) {
        return STOCK_KEY_PREFIX + activityId;
    }

    /** 供 result 轮询：Lua 已成功但 DB 尚未写入时的中间态判断。 */
    public boolean hasGrabbedUser(Long activityId, Long userId) {
        Boolean member = redis.opsForSet().isMember(usersKey(activityId), String.valueOf(userId));
        return Boolean.TRUE.equals(member);
    }

    private String usersKey(Long activityId) {
        return USERS_KEY_PREFIX + activityId;
    }
}
