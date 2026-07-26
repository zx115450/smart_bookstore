package com.zx;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量级单测：不启动完整 Spring Boot 容器。
 * 含本机「分桶近似滑动窗口」热点计数示例（对应缓存 C5-B 思路）。
 */
class FactoryTestDemoApplicationTests {

    /** bookId → (bucketIndex → count) */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Long>> map = new ConcurrentHashMap<>();

    private static final long WINDOW_MS = 60_000L;
    private static final long BUCKET_MS = 10_000L;
    private static final long THRESHOLD = 50L;

    @Test
    void contextLoads() {
        assertTrue(true);
    }

    @Test
    void bucketSlidingWindow_shouldDropExpiredAndSumAlive() {
        long id = 1L;
        long now = System.currentTimeMillis();
        long currentBucket = now / BUCKET_MS;
        long minBucket = currentBucket - (WINDOW_MS / BUCKET_MS) + 1;

        ConcurrentHashMap<Long, Long> tong =
                map.computeIfAbsent(id, k -> new ConcurrentHashMap<>());

        tong.put(minBucket - 1, 100L);

        tong.put(currentBucket - 1, 20L);
        tong.merge(currentBucket, 35L, Long::sum);

        tong.entrySet().removeIf(e -> e.getKey() < minBucket);

        long sum = 0L;
        for (Map.Entry<Long, Long> entry : tong.entrySet()) {
            sum += entry.getValue();
        }

        assertFalse(tong.containsKey(minBucket - 1));
        assertEquals(55L, sum);
        assertTrue(sum >= THRESHOLD);
    }

    @Test
    void recordAccess_shouldIncrementCurrentBucket() {
        long id = 2L;
        recordAccess(id);
        recordAccess(id);
        recordAccess(id);

        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        assertEquals(3L, map.get(id).get(nowBucket));
        assertTrue(sumInWindow(id) >= 3L);
        assertFalse(isHot(id)); // 未达 threshold=50
    }

    private void recordAccess(long bookId) {
        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        long minBucket = nowBucket - (WINDOW_MS / BUCKET_MS) + 1;
        ConcurrentHashMap<Long, Long> tong =
                map.computeIfAbsent(bookId, k -> new ConcurrentHashMap<>());
        tong.merge(nowBucket, 1L, Long::sum);
        tong.entrySet().removeIf(e -> e.getKey() < minBucket);
    }

    private long sumInWindow(long bookId) {
        ConcurrentHashMap<Long, Long> tong = map.get(bookId);
        if (tong == null) {
            return 0L;
        }
        long nowBucket = System.currentTimeMillis() / BUCKET_MS;
        long minBucket = nowBucket - (WINDOW_MS / BUCKET_MS) + 1;
        tong.entrySet().removeIf(e -> e.getKey() < minBucket);
        return tong.values().stream().mapToLong(Long::longValue).sum();
    }

    private boolean isHot(long bookId) {
        return sumInWindow(bookId) >= THRESHOLD;
    }
}
