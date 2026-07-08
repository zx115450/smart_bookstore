package com.zx.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("reservation_order")
public class ReservationOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("time_slot_id")
    private Long timeSlotId;

    @TableField("seat_id")
    private Long seatId;

    @TableField("status")
    private String status;

    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("booked_at")
    private LocalDateTime bookedAt;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("checkin_at")
    private LocalDateTime checkinAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Long getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(Long timeSlotId) { this.timeSlotId = timeSlotId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCheckinAt() { return checkinAt; }
    public void setCheckinAt(LocalDateTime checkinAt) { this.checkinAt = checkinAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
