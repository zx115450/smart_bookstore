package com.zx.bookstore.catalog.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.zx.bookstore.catalog.service.BookLocalCacheService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 图书详情缓存读路径的业务指标。
 * <p>
 * 指标全部通过 Micrometer 注册到 {@link MeterRegistry}，最终经 Spring Boot Actuator
 * 的 {@code /actuator/prometheus} 暴露。Counter 禁止携带高基数标签（如 bookId）。
 */
@Component
public class BookCacheMetrics {

    private static final String PREFIX = "bookstore.book.cache";

    private final Counter requests;
    private final Counter l1Hits;
    private final Counter l2Hits;
    private final Counter misses;
    private final Counter bloomRejects;
    private final Counter bloomFalsePositives;
    private final Timer loadTimer;

    public BookCacheMetrics(MeterRegistry registry, BookLocalCacheService localCache) {
        this.requests = Counter.builder(metricName("requests"))
                .description("Book detail read requests")
                .register(registry);
        this.l1Hits = Counter.builder(metricName("l1.hits"))
                .description("L1 Caffeine hits")
                .register(registry);
        this.l2Hits = Counter.builder(metricName("l2.hits"))
                .description("L2 Redis hits")
                .register(registry);
        this.misses = Counter.builder(metricName("misses"))
                .description("Cache miss, loaded from DB")
                .register(registry);
        this.bloomRejects = Counter.builder(metricName("bloom.rejects"))
                .description("Bloom rejected (likely not exist)")
                .register(registry);
        this.bloomFalsePositives = Counter.builder(metricName("bloom.false.positives"))
                .description("Bloom passed but DB miss")
                .register(registry);
        this.loadTimer = Timer.builder(metricName("load"))
                .description("DB load + cache put on miss")
                .register(registry);

        // 可选：把 Caffeine 内部状态以 Gauge 暴露，仅作辅助；命中率以业务 Counter 为准。
        Gauge.builder(metricName("l1.size"), localCache, BookLocalCacheService::estimatedSize)
                .description("L1 estimated entry count")
                .register(registry);
        Gauge.builder(metricName("l1.hit.rate"), localCache, c -> c.stats().hitRate())
                .description("Caffeine internal hit rate (0~1)")
                .register(registry);
    }

    private static String metricName(String suffix) {
        return PREFIX + "." + suffix;
    }

    public void onRequest() {
        requests.increment();
    }

    public void onL1Hit() {
        l1Hits.increment();
    }

    public void onL2Hit() {
        l2Hits.increment();
    }

    public void onMiss() {
        misses.increment();
    }

    public void onBloomReject() {
        bloomRejects.increment();
    }

    public void onBloomFalsePositive() {
        bloomFalsePositives.increment();
    }

    /**
     * 记录 miss 回源耗时（DB 查询 + 组装 + 写缓存）。
     *
     * @param load 回源逻辑，执行前后会被 Timer 包裹
     */
    public void recordLoad(Runnable load) {
        loadTimer.record(load);
    }

    /**
     * 记录 miss 回源耗时。
     *
     * @param duration 已测得的耗时
     */
    public void recordLoad(Duration duration) {
        loadTimer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }
}
