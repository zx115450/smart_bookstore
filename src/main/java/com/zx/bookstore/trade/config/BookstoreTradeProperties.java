package com.zx.bookstore.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookstore.trade")
public class BookstoreTradeProperties {

    /** 未支付订单自动取消延迟（分钟），默认 15。 */
    private int unpaidCancelDelayMinutes = 15;

    /**
     * 可选：毫秒级延迟，用于联调/测试（非空时优先于 unpaidCancelDelayMinutes）。
     * 例：{@code BOOKSTORE_TRADE_CANCEL_DELAY_MS=60000} 表示 1 分钟。
     */
    private Long unpaidCancelDelayMs;

    public int getUnpaidCancelDelayMinutes() {
        return unpaidCancelDelayMinutes;
    }

    public void setUnpaidCancelDelayMinutes(int unpaidCancelDelayMinutes) {
        this.unpaidCancelDelayMinutes = unpaidCancelDelayMinutes;
    }

    public Long getUnpaidCancelDelayMs() {
        return unpaidCancelDelayMs;
    }

    public void setUnpaidCancelDelayMs(Long unpaidCancelDelayMs) {
        this.unpaidCancelDelayMs = unpaidCancelDelayMs;
    }

    public long resolveCancelDelayMs() {
        if (unpaidCancelDelayMs != null && unpaidCancelDelayMs > 0) {
            return unpaidCancelDelayMs;
        }
        return Math.max(1, unpaidCancelDelayMinutes) * 60_000L;
    }
}
