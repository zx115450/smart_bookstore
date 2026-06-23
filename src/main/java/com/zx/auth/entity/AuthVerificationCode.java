package com.zx.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_verification_code")
public class AuthVerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_type", nullable = false, length = 64)
    private String loginType;

    @Column(nullable = false, length = 128)
    private String target;

    @Column(nullable = false, length = 32)
    private String scene;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "send_ip", length = 45)
    private String sendIp;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { LocalDateTime now = LocalDateTime.now(); createdAt = now; }

    public Long getId() { return id; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public String getSendIp() { return sendIp; }
    public void setSendIp(String sendIp) { this.sendIp = sendIp; }
}

