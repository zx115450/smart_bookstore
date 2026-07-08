package com.zx.reservation.dto;

public class CreateOrderRequest {
    private Long timeSlotId;
    private Long seatId;
    private String idempotencyKey;

    public Long getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(Long timeSlotId) { this.timeSlotId = timeSlotId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
