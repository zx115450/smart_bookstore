package com.zx.bookstore.catalog.metrics;

import com.zx.bookstore.catalog.service.BookLocalCacheService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCacheMetricsTest {

    @Mock
    private BookLocalCacheService localCache;

    @Test
    void counters_shouldIncrement() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookCacheMetrics metrics = new BookCacheMetrics(registry, localCache);

        metrics.onRequest();
        metrics.onL1Hit();
        metrics.onL2Hit();
        metrics.onMiss();
        metrics.onBloomReject();
        metrics.onBloomFalsePositive();

        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.requests"));
        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.l1.hits"));
        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.l2.hits"));
        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.misses"));
        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.bloom.rejects"));
        assertEquals(1.0, counterCount(registry, "bookstore.book.cache.bloom.false.positives"));
    }

    @Test
    void loadTimer_shouldRecordDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookCacheMetrics metrics = new BookCacheMetrics(registry, localCache);

        metrics.recordLoad(Duration.ofMillis(123));

        assertEquals(1.0, registry.find("bookstore.book.cache.load").timer().count());
    }

    @Test
    void loadTimer_shouldRecordRunnable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookCacheMetrics metrics = new BookCacheMetrics(registry, localCache);

        metrics.recordLoad(() -> {
            // no-op for timing test
        });

        assertEquals(1.0, registry.find("bookstore.book.cache.load").timer().count());
    }

    @Test
    void gauges_shouldRegisterOnConstruction() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(localCache.estimatedSize()).thenReturn(5L);

        new BookCacheMetrics(registry, localCache);

        Gauge sizeGauge = registry.find("bookstore.book.cache.l1.size").gauge();
        assertNotNull(sizeGauge);
        assertEquals(5.0, sizeGauge.value());

        Gauge hitRateGauge = registry.find("bookstore.book.cache.l1.hit.rate").gauge();
        assertNotNull(hitRateGauge);
    }

    private static double counterCount(SimpleMeterRegistry registry, String name) {
        return registry.find(name).counter().count();
    }
}
