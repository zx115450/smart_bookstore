package com.zx.marketing.checkin.dto;

public class CheckinRequest {

    private Long reservationOrderId;
    private Long venueId;
    private String code;

    public Long getReservationOrderId() { return reservationOrderId; }
    public void setReservationOrderId(Long reservationOrderId) { this.reservationOrderId = reservationOrderId; }
    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
