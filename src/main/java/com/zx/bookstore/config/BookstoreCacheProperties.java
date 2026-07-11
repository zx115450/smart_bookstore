package com.zx.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.cache")
public class BookstoreCacheProperties {

    private boolean enabled = true;
    private long detailTtlSeconds = 1800;
    private long detailTtlJitterSeconds = 300;

    /** 图书 ID 布隆过滤器（Redis Bitmap），防详情查询缓存穿透。 */
    private boolean bloomEnabled = true;
    /** 预期图书总量 n，用于计算位数组长度 m。 */
    private long bloomExpectedElements = 100_000L;
    /** 目标假阳性率 p（如 0.01 = 1% 误判）。 */
    private double bloomFalsePositiveRate = 0.01;
    /** 启动时从 DB 全量 rebuild 布隆（见 BookBloomWarmupRunner）。 */
    private boolean bloomWarmupOnStartup = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDetailTtlSeconds() { return detailTtlSeconds; }
    public void setDetailTtlSeconds(long detailTtlSeconds) { this.detailTtlSeconds = detailTtlSeconds; }
    public long getDetailTtlJitterSeconds() { return detailTtlJitterSeconds; }
    public void setDetailTtlJitterSeconds(long detailTtlJitterSeconds) { this.detailTtlJitterSeconds = detailTtlJitterSeconds; }

    public boolean isBloomEnabled() { return bloomEnabled; }
    public void setBloomEnabled(boolean bloomEnabled) { this.bloomEnabled = bloomEnabled; }
    public long getBloomExpectedElements() { return bloomExpectedElements; }
    public void setBloomExpectedElements(long bloomExpectedElements) { this.bloomExpectedElements = bloomExpectedElements; }
    public double getBloomFalsePositiveRate() { return bloomFalsePositiveRate; }
    public void setBloomFalsePositiveRate(double bloomFalsePositiveRate) { this.bloomFalsePositiveRate = bloomFalsePositiveRate; }
    public boolean isBloomWarmupOnStartup() { return bloomWarmupOnStartup; }
    public void setBloomWarmupOnStartup(boolean bloomWarmupOnStartup) { this.bloomWarmupOnStartup = bloomWarmupOnStartup; }
}
