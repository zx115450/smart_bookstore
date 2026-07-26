package com.zx.bookstore.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.config.BookstoreCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图书详情 Redis 缓存：Cache-Aside + TTL 随机偏移防雪崩。
 * Key: book:detail:{id}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookRedisService {

    private static final String KEY_PREFIX = "book:detail:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final BookstoreCacheProperties cacheProperties;
    private final BookHotKeyService hotKeyService;

    public Optional<BookResponse> get(Long bookId) {
        if (bookId == null || !cacheProperties.isEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(bookId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, BookResponse.class));
        } catch (Exception e) {
            log.warn("read book cache failed, id={}, err={}", bookId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(BookResponse response) {
        if (response == null || response.getId() == null || !cacheProperties.isEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            Duration ttl = resolveTtl(response.getId());
            redis.opsForValue().set(key(response.getId()), json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("write book cache failed, id={}, err={}", response.getId(), e.getMessage());
        }
    }

    public void evict(Long bookId) {
        if (bookId == null || !cacheProperties.isEnabled()) {
            return;
        }
        redis.delete(key(bookId));
    }

    private Duration resolveTtl(Long bookId) {
        if (hotKeyService != null && hotKeyService.isHot(bookId)) {
            return ttlWithJitter(
                    cacheProperties.getHotDetailTtlSeconds(),
                    cacheProperties.getHotDetailTtlJitterSeconds());
        }
        return ttlWithJitter(
                cacheProperties.getDetailTtlSeconds(),
                cacheProperties.getDetailTtlJitterSeconds());
    }

    private Duration ttlWithJitter(long baseSeconds, long jitterSeconds) {
        long base = Math.max(1, baseSeconds);
        long jitter = Math.max(0, jitterSeconds);
        long ttl = base + (jitter > 0 ? ThreadLocalRandom.current().nextLong(0, jitter) : 0);
        return Duration.ofSeconds(ttl);
    }

    private String key(Long bookId) {
        return KEY_PREFIX + bookId;
    }
}
