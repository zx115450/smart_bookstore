package com.zx.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.cache")
public class BookstoreCacheProperties {

    private boolean enabled = true;
    private long detailTtlSeconds = 1800;
    private long detailTtlJitterSeconds = 300;

    /** 是否启用 Caffeine L1（进程内）；false 时仅 Redis L2。 */
    private boolean localEnabled = true;
    /** L1 最大条目数。 */
    private long localMaxSize = 2000L;
    /** L1 写入后过期秒数（宜短于 Redis TTL，降低多实例脏读窗口）。 */
    private long localExpireSeconds = 60L;

    /** 热点探测：是否启用。 */
    private boolean hotKeyEnabled = true;
    /** 滑动窗口长度（秒）。 */
    private long hotWindowSeconds = 60L;
    /** 分桶宽度（秒）。 */
    private long hotBucketSeconds = 10L;
    /** 窗口内访问次数 ≥ 此阈值视为热点。 */
    private long hotThreshold = 50L;
    /** 热点 Redis 详情 TTL（秒）。 */
    private long hotDetailTtlSeconds = 7200L;
    /** 热点 TTL 抖动（秒）。 */
    private long hotDetailTtlJitterSeconds = 600L;
    /** 最多维护多少个 bookId 的计数；超出时淘汰。 */
    private long hotMaxTrackedKeys = 5000L;

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

    public boolean isLocalEnabled() { return localEnabled; }
    public void setLocalEnabled(boolean localEnabled) { this.localEnabled = localEnabled; }
    public long getLocalMaxSize() { return localMaxSize; }
    public void setLocalMaxSize(long localMaxSize) { this.localMaxSize = localMaxSize; }
    public long getLocalExpireSeconds() { return localExpireSeconds; }
    public void setLocalExpireSeconds(long localExpireSeconds) { this.localExpireSeconds = localExpireSeconds; }

    public boolean isHotKeyEnabled() { return hotKeyEnabled; }
    public void setHotKeyEnabled(boolean hotKeyEnabled) { this.hotKeyEnabled = hotKeyEnabled; }
    public long getHotWindowSeconds() { return hotWindowSeconds; }
    public void setHotWindowSeconds(long hotWindowSeconds) { this.hotWindowSeconds = hotWindowSeconds; }
    public long getHotBucketSeconds() { return hotBucketSeconds; }
    public void setHotBucketSeconds(long hotBucketSeconds) { this.hotBucketSeconds = hotBucketSeconds; }
    public long getHotThreshold() { return hotThreshold; }
    public void setHotThreshold(long hotThreshold) { this.hotThreshold = hotThreshold; }
    public long getHotDetailTtlSeconds() { return hotDetailTtlSeconds; }
    public void setHotDetailTtlSeconds(long hotDetailTtlSeconds) { this.hotDetailTtlSeconds = hotDetailTtlSeconds; }
    public long getHotDetailTtlJitterSeconds() { return hotDetailTtlJitterSeconds; }
    public void setHotDetailTtlJitterSeconds(long hotDetailTtlJitterSeconds) { this.hotDetailTtlJitterSeconds = hotDetailTtlJitterSeconds; }
    public long getHotMaxTrackedKeys() { return hotMaxTrackedKeys; }
    public void setHotMaxTrackedKeys(long hotMaxTrackedKeys) { this.hotMaxTrackedKeys = hotMaxTrackedKeys; }

    public boolean isBloomEnabled() { return bloomEnabled; }
    public void setBloomEnabled(boolean bloomEnabled) { this.bloomEnabled = bloomEnabled; }
    public long getBloomExpectedElements() { return bloomExpectedElements; }
    public void setBloomExpectedElements(long bloomExpectedElements) { this.bloomExpectedElements = bloomExpectedElements; }
    public double getBloomFalsePositiveRate() { return bloomFalsePositiveRate; }
    public void setBloomFalsePositiveRate(double bloomFalsePositiveRate) { this.bloomFalsePositiveRate = bloomFalsePositiveRate; }
    public boolean isBloomWarmupOnStartup() { return bloomWarmupOnStartup; }
    public void setBloomWarmupOnStartup(boolean bloomWarmupOnStartup) { this.bloomWarmupOnStartup = bloomWarmupOnStartup; }
}
