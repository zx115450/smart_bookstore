package com.zx.bookstore.cache.logical;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 逻辑过期缓存载荷：Redis Key 可仍存在，是否「过期」看 {@link #expireTime}。
 */
public class RedisLogicalData {

    /** 逻辑过期时间；到达后可返回旧 data，并触发异步重建。 */
    private LocalDateTime expireTime;

    /** 业务 JSON；{@link #absent} 为 true 时忽略。 */
    private JsonNode data;

    /** true 表示 DB 无记录（空值缓存，防穿透）。 */
    private boolean absent;

    public RedisLogicalData() {
    }

    public RedisLogicalData(LocalDateTime expireTime, JsonNode data, boolean absent) {
        this.expireTime = expireTime;
        this.data = data;
        this.absent = absent;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    public boolean isAbsent() {
        return absent;
    }

    public void setAbsent(boolean absent) {
        this.absent = absent;
    }
}
