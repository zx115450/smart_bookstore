package com.zx.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("auth_session")
public class AuthSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField(exist = false)
    private AuthUser user;

    @TableField("refresh_token_jti")
    private String refreshTokenJti;

    @TableField("refresh_token_hash")
    private String refreshTokenHash;

    @TableField("access_expires_at")
    private LocalDateTime accessExpiresAt;

    @TableField("refresh_expires_at")
    private LocalDateTime refreshExpiresAt;

    @TableField("remember_me")
    private Boolean rememberMe = false;

    @TableField("device_info")
    private String deviceInfo;

    @TableField("login_ip")
    private String loginIp;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public AuthUser getUser() { return user; }
    public void setUser(AuthUser user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }
    public String getRefreshTokenJti() { return refreshTokenJti; }
    public void setRefreshTokenJti(String refreshTokenJti) { this.refreshTokenJti = refreshTokenJti; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }
    public LocalDateTime getAccessExpiresAt() { return accessExpiresAt; }
    public void setAccessExpiresAt(LocalDateTime accessExpiresAt) { this.accessExpiresAt = accessExpiresAt; }
    public LocalDateTime getRefreshExpiresAt() { return refreshExpiresAt; }
    public void setRefreshExpiresAt(LocalDateTime refreshExpiresAt) { this.refreshExpiresAt = refreshExpiresAt; }
    public Boolean getRememberMe() { return rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
