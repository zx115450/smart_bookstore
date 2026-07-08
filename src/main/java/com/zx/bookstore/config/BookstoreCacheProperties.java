package com.zx.bookstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.cache")
public class BookstoreCacheProperties {

    private boolean enabled = true;
    private long detailTtlSeconds = 1800;
    private long detailTtlJitterSeconds = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getDetailTtlSeconds() { return detailTtlSeconds; }
    public void setDetailTtlSeconds(long detailTtlSeconds) { this.detailTtlSeconds = detailTtlSeconds; }
    public long getDetailTtlJitterSeconds() { return detailTtlJitterSeconds; }
    public void setDetailTtlJitterSeconds(long detailTtlJitterSeconds) { this.detailTtlJitterSeconds = detailTtlJitterSeconds; }
}
