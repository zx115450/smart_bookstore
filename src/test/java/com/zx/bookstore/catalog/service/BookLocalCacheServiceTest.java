package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.config.BookstoreCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLocalCacheServiceTest {

    private BookstoreCacheProperties props;
    private BookLocalCacheService localCache;

    @BeforeEach
    void setUp() {
        props = new BookstoreCacheProperties();
        props.setEnabled(true);
        props.setLocalEnabled(true);
        props.setLocalMaxSize(100);
        props.setLocalExpireSeconds(60);
        localCache = new BookLocalCacheService(props);
        localCache.init();
    }

    @Test
    void putThenGet_shouldHit() {
        BookResponse book = sample(11L, "Java 核心技术");
        localCache.put(book);

        assertTrue(localCache.get(11L).isPresent());
        assertEquals("Java 核心技术", localCache.get(11L).get().getTitle());
    }

    @Test
    void evict_shouldMiss() {
        localCache.put(sample(12L, "Redis"));
        localCache.evict(12L);
        assertTrue(localCache.get(12L).isEmpty());
    }

    @Test
    void localDisabled_shouldAlwaysMiss() {
        props.setLocalEnabled(false);
        localCache.put(sample(13L, "hidden"));
        assertTrue(localCache.get(13L).isEmpty());
    }

    private static BookResponse sample(Long id, String title) {
        BookResponse r = new BookResponse();
        r.setId(id);
        r.setTitle(title);
        r.setPrice(BigDecimal.TEN);
        return r;
    }
}
