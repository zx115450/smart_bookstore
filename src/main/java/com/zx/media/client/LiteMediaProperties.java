package com.zx.media.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lite VOD 媒资客户端配置（书城只经 Client 调媒资，不直连媒资库）。
 * <p>
 * {@code BOOKSTORE_MEDIA_TOKEN} 须与 vod-api 的 {@code VOD_INTERNAL_TOKEN} 一致。
 */
@ConfigurationProperties(prefix = "bookstore.media.lite-vod")
public class LiteMediaProperties {

    /** 总开关；false 时应用仍可启动，阅读接口提示媒资未启用。 */
    private boolean enabled = true;

    /** true 时使用内存三章样例，不访问 base-url。 */
    private boolean mock = true;

    /** vod-api 根地址，如 http://127.0.0.1:8080 */
    private String baseUrl = "http://127.0.0.1:8080";

    /** 内部接口 Token，对应请求头 X-Internal-Token */
    private String internalToken = "";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 10000;

    /** object-url 默认 TTL（秒） */
    private int objectUrlTtlSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getObjectUrlTtlSeconds() {
        return objectUrlTtlSeconds;
    }

    public void setObjectUrlTtlSeconds(int objectUrlTtlSeconds) {
        this.objectUrlTtlSeconds = objectUrlTtlSeconds;
    }
}
