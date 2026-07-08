package com.zx.marketing.checkin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("checkin_streak")
public class CheckinStreak {

    @TableId("user_id")
    private Long userId;

    @TableField("current_streak")
    private Integer currentStreak = 0;

    @TableField("last_checkin_date")
    private LocalDate lastCheckinDate;

    @TableField("total_checkins")
    private Integer totalCheckins = 0;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(Integer currentStreak) { this.currentStreak = currentStreak; }
    public LocalDate getLastCheckinDate() { return lastCheckinDate; }
    public void setLastCheckinDate(LocalDate lastCheckinDate) { this.lastCheckinDate = lastCheckinDate; }
    public Integer getTotalCheckins() { return totalCheckins; }
    public void setTotalCheckins(Integer totalCheckins) { this.totalCheckins = totalCheckins; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
