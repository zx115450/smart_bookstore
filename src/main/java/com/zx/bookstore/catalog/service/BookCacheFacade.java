package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.BookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 图书详情二级缓存门面：L1 Caffeine + L2 Redis。
 * <p>
 * 读：先 L1 再 L2（L2 命中回填 L1）；写回填：L2 + L1；失效：两层一起清。
 */
@Service
@RequiredArgsConstructor
public class BookCacheFacade {

    private final BookLocalCacheService localCache;
    private final BookRedisService redisCache;

    public Optional<BookResponse> getLocal(Long bookId) {
        return localCache.get(bookId);
    }

    public Optional<BookResponse> getRedis(Long bookId) {
        return redisCache.get(bookId);
    }

    /** L2 命中后回填 L1。 */
    public void fillLocal(BookResponse response) {
        localCache.put(response);
    }

    /** DB 回源后写入 L2 与 L1。 */
    public void put(BookResponse response) {
        redisCache.put(response);
        localCache.put(response);
    }

    /** 更新 / 下架后同时失效两级缓存。 */
    public void evict(Long bookId) {
        redisCache.evict(bookId);
        localCache.evict(bookId);
    }
}
