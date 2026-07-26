package com.zx.bookstore.cache.logical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 逻辑过期 + 互斥锁 + 异步重建的通用缓存客户端（防热 Key 击穿）。
 * <p>
 * 用法示例：
 * <pre>
 * cacheClient.queryWithLogicalExpire(
 *     "book:logical:", "book:lock:", id, BookResponse.class,
 *     Duration.ofMinutes(30), bookId -> loadFromDb(bookId));
 * </pre>
 * 与图书详情默认的 Cache Aside（物理 TTL + L1/L2）互补：本组件适合「可接受短暂脏读的超级热点读」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogicalExpireCacheClient {

    private static final String LOCK_VALUE = "1";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration WAIT_OTHERS_TIMEOUT = Duration.ofSeconds(3);
    private static final long WAIT_SLEEP_MS = 100L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ExecutorService rebuildExecutor = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "logical-expire-rebuild");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void destroy() {
        rebuildExecutor.shutdown();
        try {
            if (!rebuildExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                rebuildExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            rebuildExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void save(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException("redis save failed, key=" + key, e);
        }
    }

    public void save(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("redis save failed, key=" + key, e);
        }
    }

    /**
     * 写入逻辑过期载荷；物理 TTL = 逻辑时长 + 缓冲，保证逻辑过期后 Key 仍在，可返回旧值。
     */
    public void saveWithLogicalExpire(String key, Object value, Duration logicalTtl) {
        try {
            JsonNode data = objectMapper.valueToTree(value);
            RedisLogicalData payload = new RedisLogicalData(
                    LocalDateTime.now().plus(logicalTtl), data, false);
            Duration physicalTtl = logicalTtl.plus(Duration.ofHours(1));
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), physicalTtl);
        } catch (Exception e) {
            throw new IllegalStateException("redis logical save failed, key=" + key, e);
        }
    }

    /** 空值短物理 TTL，防穿透。 */
    public void saveAbsent(String key, Duration physicalTtl) {
        try {
            RedisLogicalData payload = new RedisLogicalData(
                    LocalDateTime.now().plus(physicalTtl), null, true);
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(payload), physicalTtl);
        } catch (Exception e) {
            throw new IllegalStateException("redis absent save failed, key=" + key, e);
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 带逻辑过期的查询：未过期直接返回；过期则返回旧值并异步重建；完全 miss 则互斥回源。
     *
     * @param keyPrefix      业务 Key 前缀
     * @param lockKeyPrefix  锁 Key 前缀
     * @param id             业务 id
     * @param type           返回类型
     * @param logicalTtl     逻辑过期时长
     * @param dbLoader       miss / 重建时查库
     * @return 业务对象；DB 无记录抛 {@link CacheMissException}
     */
    public <R, T> R queryWithLogicalExpire(String keyPrefix,
                                           String lockKeyPrefix,
                                           T id,
                                           Class<R> type,
                                           Duration logicalTtl,
                                           Function<T, R> dbLoader) {
        String key = keyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String json = ops.get(key);

        if (json == null) {
            return loadOnMiss(key, lockKey, id, type, logicalTtl, dbLoader, ops);
        }

        RedisLogicalData payload = readPayload(json);
        if (payload.isAbsent()) {
            throw new CacheMissException("不存在该项, id=" + id);
        }
        R current = convert(payload.getData(), type);

        if (!isLogicallyExpired(payload)) {
            return current;
        }

        // 逻辑过期：抢到锁则异步重建（锁在异步任务内释放），否则直接返回旧值
        if (!tryLock(lockKey)) {
            return current;
        }
        rebuildExecutor.execute(() -> {
            try {
                log.info("logical-expire rebuild start, key={}", key);
                R fresh = dbLoader.apply(id);
                if (fresh != null) {
                    saveWithLogicalExpire(key, fresh, logicalTtl);
                } else {
                    saveAbsent(key, NULL_CACHE_TTL);
                }
            } catch (Exception e) {
                log.warn("logical-expire rebuild failed, key={}, err={}", key, e.getMessage());
            } finally {
                unlock(lockKey);
            }
        });
        return current;
    }

    private <R, T> R loadOnMiss(String key,
                                String lockKey,
                                T id,
                                Class<R> type,
                                Duration logicalTtl,
                                Function<T, R> dbLoader,
                                ValueOperations<String, String> ops) {
        if (!tryLock(lockKey)) {
            R waited = waitForCache(key, type, ops);
            if (waited != null) {
                return waited;
            }
            if (isAbsentCached(key, ops)) {
                throw new CacheMissException("不存在该项, id=" + id);
            }
            // 等待超时：再抢一次；仍失败则直接查库降级（不写缓存）
            if (!tryLock(lockKey)) {
                R fallback = dbLoader.apply(id);
                if (fallback == null) {
                    throw new CacheMissException("不存在该项, id=" + id);
                }
                return fallback;
            }
        }

        try {
            log.info("logical-expire create cache, key={}", key);
            R loaded = dbLoader.apply(id);
            if (loaded != null) {
                saveWithLogicalExpire(key, loaded, logicalTtl);
                return loaded;
            }
            saveAbsent(key, NULL_CACHE_TTL);
            throw new CacheMissException("不存在该项, id=" + id);
        } finally {
            unlock(lockKey);
        }
    }

    private <R> R waitForCache(String key, Class<R> type, ValueOperations<String, String> ops) {
        long deadline = System.nanoTime() + WAIT_OTHERS_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            sleepQuietly(WAIT_SLEEP_MS);
            String json = ops.get(key);
            if (json == null) {
                continue;
            }
            RedisLogicalData payload = readPayload(json);
            if (payload.isAbsent()) {
                return null;
            }
            return convert(payload.getData(), type);
        }
        return null;
    }

    private boolean isAbsentCached(String key, ValueOperations<String, String> ops) {
        String json = ops.get(key);
        if (json == null) {
            return false;
        }
        return readPayload(json).isAbsent();
    }

    private boolean isLogicallyExpired(RedisLogicalData payload) {
        LocalDateTime expireTime = payload.getExpireTime();
        return expireTime == null || LocalDateTime.now().isAfter(expireTime);
    }

    private RedisLogicalData readPayload(String json) {
        try {
            return objectMapper.readValue(json, RedisLogicalData.class);
        } catch (Exception e) {
            throw new IllegalStateException("invalid logical cache payload", e);
        }
    }

    private <R> R convert(JsonNode data, Class<R> type) {
        if (data == null || data.isNull()) {
            throw new IllegalStateException("logical cache data is null");
        }
        try {
            return objectMapper.treeToValue(data, type);
        } catch (Exception e) {
            throw new IllegalStateException("logical cache deserialize failed", e);
        }
    }

    private boolean tryLock(String lockKey) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, LOCK_VALUE, LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for cache", e);
        }
    }

    /** DB 无数据或命中空值缓存时抛出。 */
    public static class CacheMissException extends RuntimeException {
        public CacheMissException(String message) {
            super(message);
        }
    }
}
