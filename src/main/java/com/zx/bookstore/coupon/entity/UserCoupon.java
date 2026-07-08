package com.zx.bookstore.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_coupon")
public class UserCoupon {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("template_id")
    private Long templateId;

    @TableField("status")
    private String status;

    @TableField("obtain_way")
    private String obtainWay;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("trade_order_id")
    private Long tradeOrderId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObtainWay() { return obtainWay; }
    public void setObtainWay(String obtainWay) { this.obtainWay = obtainWay; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public Long getTradeOrderId() { return tradeOrderId; }
    public void setTradeOrderId(Long tradeOrderId) { this.tradeOrderId = tradeOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
