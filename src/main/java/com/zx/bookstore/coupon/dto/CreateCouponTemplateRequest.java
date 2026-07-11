package com.zx.bookstore.coupon.dto;

import java.math.BigDecimal;

public class CreateCouponTemplateRequest {
    private String name;
    private String couponType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private Integer totalCount;
    private Integer validDays;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCouponType() { return couponType; }
    public void setCouponType(String couponType) { this.couponType = couponType; }
    public BigDecimal getThresholdAmount() { return thresholdAmount; }
    public void setThresholdAmount(BigDecimal thresholdAmount) { this.thresholdAmount = thresholdAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
}
