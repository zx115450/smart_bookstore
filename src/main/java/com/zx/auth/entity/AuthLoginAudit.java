package com.zx.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auth_login_audit")
public class AuthLoginAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AuthUser user;

    @Column(name = "login_type", nullable = false)
    private String loginType;

    @Column
    private String target;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public AuthUser getUser() { return user; }
    public void setUser(AuthUser user) { this.user = user; }
    public String getLoginType() { return loginType; }
    public void setLoginType(String loginType) { this.loginType = loginType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}

