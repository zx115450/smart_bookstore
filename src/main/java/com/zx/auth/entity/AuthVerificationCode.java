package com.zx.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("auth_verification_code")
public class AuthVerificationCode {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("login_type")
    private String loginType;

    @TableField("target")
    private String target;

    @TableField("scene")
    private String scene;

    @TableField("code_hash")
    private String codeHash;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("send_ip")
    private String sendIp;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
