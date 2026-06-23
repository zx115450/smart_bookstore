package com.zx.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_session")
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AuthUser user;

    @Column(name = "refresh_token_jti", nullable = false, length = 36)
    private String refreshTokenJti;

    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    private String refreshTokenHash;

    @Column(name = "access_expires_at", nullable = false)
    private LocalDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "remember_me", nullable = false)
    private Boolean rememberMe = false;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "login_ip", length = 45)
    private String loginIp;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public AuthUser getUser() { return user; }
    public void setUser(AuthUser user) { this.user = user; }
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
}

