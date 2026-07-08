package com.zx.reservation.dto;

public class OrderResponse {
    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    private Long resourceId;
    private String resourceName;
    private Long timeSlotId;
    private Long seatId;
    private String seatNo;
    private String slotDate;
    private String startTime;
    private String endTime;
    private String status;
    private String cancelReason;
    private String bookedAt;
    private String cancelledAt;
    private String completedAt;
    private String checkinAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public Long getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(Long timeSlotId) { this.timeSlotId = timeSlotId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public String getSlotDate() { return slotDate; }
    public void setSlotDate(String slotDate) { this.slotDate = slotDate; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getBookedAt() { return bookedAt; }
    public void setBookedAt(String bookedAt) { this.bookedAt = bookedAt; }
    public String getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public String getCheckinAt() { return checkinAt; }
    public void setCheckinAt(String checkinAt) { this.checkinAt = checkinAt; }
}
