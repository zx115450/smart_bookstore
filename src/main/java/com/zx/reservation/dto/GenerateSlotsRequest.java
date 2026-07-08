package com.zx.reservation.dto;

public class GenerateSlotsRequest {
    private String date;
    private String endDate;
    private Integer startHour = 8;
    private Integer endHour = 22;
    private Integer durationHours = 2;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }
    public Integer getEndHour() { return endHour; }
    public void setEndHour(Integer endHour) { this.endHour = endHour; }
    public Integer getDurationHours() { return durationHours; }
    public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }
}
