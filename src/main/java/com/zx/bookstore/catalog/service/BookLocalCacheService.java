package com.zx.bookstore.catalog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.config.BookstoreCacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 图书详情 L1 本地缓存（Caffeine，进程堆内存）。
 * <p>
 * 多实例不共享；靠短 TTL + 写路径 {@link #evict} 降低脏读。关闭 {@code bookstore.cache.local-enabled} 后全部空操作。
 */
@Service
@RequiredArgsConstructor
public class BookLocalCacheService {

    private final BookstoreCacheProperties cacheProperties;
    private Cache<Long, BookResponse> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .maximumSize(Math.max(100L, cacheProperties.getLocalMaxSize()))
                .expireAfterWrite(Duration.ofSeconds(Math.max(5L, cacheProperties.getLocalExpireSeconds())))
                .recordStats()
                .build();
    }

    public boolean isEnabled() {
        return cacheProperties.isEnabled() && cacheProperties.isLocalEnabled();
    }

    public Optional<BookResponse> get(Long bookId) {
        if (!isEnabled() || bookId == null || cache == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(bookId));
    }

    public void put(BookResponse response) {
        if (!isEnabled() || response == null || response.getId() == null || cache == null) {
            return;
        }
        cache.put(response.getId(), response);
    }

    public void evict(Long bookId) {
        if (bookId == null || cache == null) {
            return;
        }
        cache.invalidate(bookId);
    }

    public CacheStats stats() {
        return cache == null ? CacheStats.empty() : cache.stats();
    }

    public long estimatedSize() {
        return cache == null ? 0L : cache.estimatedSize();
    }
}
