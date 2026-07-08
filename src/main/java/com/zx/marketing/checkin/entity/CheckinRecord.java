package com.zx.marketing.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("checkin_record")
public class CheckinRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("checkin_date")
    private LocalDate checkinDate;

    @TableField("reservation_order_id")
    private Long reservationOrderId;

    @TableField("venue_id")
    private Long venueId;

    @TableField("streak_day")
    private Integer streakDay = 1;

    @TableField("reward_coupon_id")
    private Long rewardCouponId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public Long getReservationOrderId() { return reservationOrderId; }
    public void setReservationOrderId(Long reservationOrderId) { this.reservationOrderId = reservationOrderId; }
    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }
    public Integer getStreakDay() { return streakDay; }
    public void setStreakDay(Integer streakDay) { this.streakDay = streakDay; }
    public Long getRewardCouponId() { return rewardCouponId; }
    public void setRewardCouponId(Long rewardCouponId) { this.rewardCouponId = rewardCouponId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
