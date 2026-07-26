package com.zx.bookstore.catalog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zx.bookstore.config.BookstoreCacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图书详情热点 Key 探测：本机分桶近似滑动窗口。
 * <p>
 * 每个 bookId 维护一个桶计数器；窗口按配置（默认 60s / 10s 桶）滚动。
 * 被判定为热点后，下次 L2 Redis 写入会使用更长的 TTL。
 * 多实例不共享热度；对流量不均场景可后续升级为 Redis 计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookHotKeyService {

    private final BookstoreCacheProperties cacheProperties;

    /**
     * 外层缓存：bookId → 桶计数器。
     * 用 Caffeine 自动淘汰冷 bookId，避免攻击性扫描把 Map 撑爆。
     */
    private Cache<Long, ConcurrentHashMap<Long, Long>> counters;

    @PostConstruct
    void init() {
        long maxTracked = Math.max(100L, cacheProperties.getHotMaxTrackedKeys());
        long accessTtl = Math.max(60L, cacheProperties.getHotWindowSeconds() * 2);
        counters = Caffeine.newBuilder()
                .maximumSize(maxTracked)
                .expireAfterAccess(Duration.ofSeconds(accessTtl))
                .build();
    }

    public boolean isEnabled() {
        return cacheProperties.isEnabled() && cacheProperties.isHotKeyEnabled();
    }

    /**
     * 记录一次访问；在 {@link BookCatalogService#getBookDetail} 成功返回前调用。
     */
    public void recordAccess(Long bookId) {
        if (!isEnabled() || bookId == null || counters == null) {
            return;
        }
        long windowSeconds = Math.max(1L, cacheProperties.getHotWindowSeconds());
        long bucketSeconds = Math.max(1L, cacheProperties.getHotBucketSeconds());
        if (bucketSeconds > windowSeconds) {
            bucketSeconds = windowSeconds;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        long currentBucket = nowSec / bucketSeconds;
        long minBucket = currentBucket - (windowSeconds / bucketSeconds) + 1;

        ConcurrentHashMap<Long, Long> buckets =
                counters.get(bookId, k -> new ConcurrentHashMap<>());
        buckets.merge(currentBucket, 1L, Long::sum);
        buckets.entrySet().removeIf(e -> e.getKey() < minBucket);
    }

    /**
     * 判断该书在当前窗口是否已达热点阈值。
     */
    public boolean isHot(Long bookId) {
        if (!isEnabled() || bookId == null || counters == null) {
            return false;
        }
        return sumInWindow(bookId) >= cacheProperties.getHotThreshold();
    }

    private long sumInWindow(Long bookId) {
        ConcurrentHashMap<Long, Long> buckets = counters.getIfPresent(bookId);
        if (buckets == null) {
            return 0L;
        }
        long windowSeconds = Math.max(1L, cacheProperties.getHotWindowSeconds());
        long bucketSeconds = Math.max(1L, cacheProperties.getHotBucketSeconds());
        long nowSec = System.currentTimeMillis() / 1000L;
        long currentBucket = nowSec / bucketSeconds;
        long minBucket = currentBucket - (windowSeconds / bucketSeconds) + 1;
        buckets.entrySet().removeIf(e -> e.getKey() < minBucket);
        return buckets.values().stream().mapToLong(Long::longValue).sum();
    }
}
