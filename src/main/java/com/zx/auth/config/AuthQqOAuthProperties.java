package com.zx.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.oauth.qq")
public class AuthQqOAuthProperties {

    private String appId;
    private String appKey;
    private String redirectUri = "http://localhost:8081/api/auth/oauth/qq/callback";
    private String frontendSuccessUrl = "http://localhost:5173/oauth/qq/success";
    private String frontendErrorUrl = "http://localhost:5173/oauth/qq/error";
    private long stateTtlSeconds = 600;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getFrontendSuccessUrl() {
        return frontendSuccessUrl;
    }

    public void setFrontendSuccessUrl(String frontendSuccessUrl) {
        this.frontendSuccessUrl = frontendSuccessUrl;
    }

    public String getFrontendErrorUrl() {
        return frontendErrorUrl;
    }

    public void setFrontendErrorUrl(String frontendErrorUrl) {
        this.frontendErrorUrl = frontendErrorUrl;
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(long stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }
}
