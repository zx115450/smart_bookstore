package com.zx.bookstore.borrow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.borrow.overdue")
public class BookstoreBorrowOverdueProperties {

    /** 是否启用 ZSET + 定时扫描逾期。 */
    private boolean enabled = true;

    /** 扫描间隔（毫秒）。 */
    private long pollIntervalMs = 60_000L;

    /** Lua 每次弹出的最大订单数。 */
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
