package com.zx.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.rate-limit")
public class AuthRateLimitProperties {

    private int sendCodePerTargetSeconds = 60;
    private int sendCodePerTargetHourly = 5;
    private int sendCodePerIpHourly = 10;
    private int loginFailMax = 5;
    private int loginFailLockMinutes = 15;

    public int getSendCodePerTargetSeconds() {
        return sendCodePerTargetSeconds;
    }

    public void setSendCodePerTargetSeconds(int sendCodePerTargetSeconds) {
        this.sendCodePerTargetSeconds = sendCodePerTargetSeconds;
    }

    public int getSendCodePerTargetHourly() {
        return sendCodePerTargetHourly;
    }

    public void setSendCodePerTargetHourly(int sendCodePerTargetHourly) {
        this.sendCodePerTargetHourly = sendCodePerTargetHourly;
    }

    public int getSendCodePerIpHourly() {
        return sendCodePerIpHourly;
    }

    public void setSendCodePerIpHourly(int sendCodePerIpHourly) {
        this.sendCodePerIpHourly = sendCodePerIpHourly;
    }

    public int getLoginFailMax() {
        return loginFailMax;
    }

    public void setLoginFailMax(int loginFailMax) {
        this.loginFailMax = loginFailMax;
    }

    public int getLoginFailLockMinutes() {
        return loginFailLockMinutes;
    }

    public void setLoginFailLockMinutes(int loginFailLockMinutes) {
        this.loginFailLockMinutes = loginFailLockMinutes;
    }
}
