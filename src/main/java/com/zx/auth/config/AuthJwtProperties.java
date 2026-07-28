package com.zx.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    private String secret;
    private long accessExpireSeconds = 7200;
    private int refreshExpireDays = 7;
    private int refreshExpireDaysRemember = 30;
    private int sessionAbsoluteExpireDays = 90;
    private String issuer = "smart_bookstore";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }

    public void setAccessExpireSeconds(long accessExpireSeconds) {
        this.accessExpireSeconds = accessExpireSeconds;
    }

    public int getRefreshExpireDays() {
        return refreshExpireDays;
    }

    public void setRefreshExpireDays(int refreshExpireDays) {
        this.refreshExpireDays = refreshExpireDays;
    }

    public int getRefreshExpireDaysRemember() {
        return refreshExpireDaysRemember;
    }

    public void setRefreshExpireDaysRemember(int refreshExpireDaysRemember) {
        this.refreshExpireDaysRemember = refreshExpireDaysRemember;
    }

    public int getSessionAbsoluteExpireDays() {
        return sessionAbsoluteExpireDays;
    }

    public void setSessionAbsoluteExpireDays(int sessionAbsoluteExpireDays) {
        this.sessionAbsoluteExpireDays = sessionAbsoluteExpireDays;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
