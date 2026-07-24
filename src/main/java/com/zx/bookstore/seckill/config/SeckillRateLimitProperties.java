package com.zx.bookstore.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 秒杀 grab 入口令牌桶限流（Redis Lua）。
 * <p>
 * 先过活动维度桶，再过用户维度桶；均拿到令牌后才执行库存 Lua。
 */
@ConfigurationProperties(prefix = "bookstore.seckill.rate-limit")
public class SeckillRateLimitProperties {

    /** 总开关；false 时跳过限流。 */
    private boolean enabled = true;

    /** 活动桶容量（可短时突发的令牌数）。 */
    private int activityCapacity = 2000;

    /** 活动桶每秒补充令牌数。 */
    private double activityRefillPerSecond = 2000;

    /** 用户桶容量。 */
    private int userCapacity = 2;

    /** 用户桶每秒补充令牌数。 */
    private double userRefillPerSecond = 1;

    /** 限流 Key TTL（秒），防止冷 Key 长期占用内存。 */
    private long keyTtlSeconds = 7200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getActivityCapacity() {
        return activityCapacity;
    }

    public void setActivityCapacity(int activityCapacity) {
        this.activityCapacity = activityCapacity;
    }

    public double getActivityRefillPerSecond() {
        return activityRefillPerSecond;
    }

    public void setActivityRefillPerSecond(double activityRefillPerSecond) {
        this.activityRefillPerSecond = activityRefillPerSecond;
    }

    public int getUserCapacity() {
        return userCapacity;
    }

    public void setUserCapacity(int userCapacity) {
        this.userCapacity = userCapacity;
    }

    public double getUserRefillPerSecond() {
        return userRefillPerSecond;
    }

    public void setUserRefillPerSecond(double userRefillPerSecond) {
        this.userRefillPerSecond = userRefillPerSecond;
    }

    public long getKeyTtlSeconds() {
        return keyTtlSeconds;
    }

    public void setKeyTtlSeconds(long keyTtlSeconds) {
        this.keyTtlSeconds = keyTtlSeconds;
    }
}
