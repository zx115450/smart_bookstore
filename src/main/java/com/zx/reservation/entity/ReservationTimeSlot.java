package com.zx.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("reservation_time_slot")
public class ReservationTimeSlot {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("slot_date")
    private LocalDate slotDate;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("end_time")
    private LocalTime endTime;

    @TableField("capacity")
    private Integer capacity;

    @TableField("booked_count")
    private Integer bookedCount = 0;

    @Version
    @TableField("version")
    private Integer version = 0;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getBookedCount() { return bookedCount; }
    public void setBookedCount(Integer bookedCount) { this.bookedCount = bookedCount; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getRemainingCapacity() {
        int booked = bookedCount == null ? 0 : bookedCount;
        int cap = capacity == null ? 0 : capacity;
        return Math.max(0, cap - booked);
    }
}
