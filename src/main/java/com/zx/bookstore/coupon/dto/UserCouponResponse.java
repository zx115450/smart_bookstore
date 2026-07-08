package com.zx.bookstore.coupon.dto;

import java.math.BigDecimal;

public class UserCouponResponse {

    private Long id;
    private Long templateId;
    private String templateName;
    private String couponType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private String status;
    private String obtainWay;
    private String expireAt;
    private String usedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getCouponType() { return couponType; }
    public void setCouponType(String couponType) { this.couponType = couponType; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public void setThresholdAmount(BigDecimal thresholdAmount) { this.thresholdAmount = thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObtainWay() { return obtainWay; }
    public void setObtainWay(String obtainWay) { this.obtainWay = obtainWay; }
    public String getExpireAt() { return expireAt; }
    public void setExpireAt(String expireAt) { this.expireAt = expireAt; }
    public String getUsedAt() { return usedAt; }
    public void setUsedAt(String usedAt) { this.usedAt = usedAt; }
}
