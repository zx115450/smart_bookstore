package com.zx.bookstore.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("coupon_template")
public class CouponTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("coupon_type")
    private String couponType;

    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    @TableField("discount_amount")
    private BigDecimal discountAmount;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("issued_count")
    private Integer issuedCount;

    @TableField("valid_days")
    private Integer validDays;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getIssuedCount() { return issuedCount; }
    public void setIssuedCount(Integer issuedCount) { this.issuedCount = issuedCount; }
    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
