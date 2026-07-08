package com.zx.marketing.checkin.dto;

import java.util.List;

public class CheckinResponse {

    private String checkinDate;
    private int streak;
    private int streakDay;
    private boolean rewarded;
    private Long rewardCouponId;
    private Long reservationOrderId;
    private String venueName;

    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }
    public int getStreak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }
    public int getStreakDay() { return streakDay; }
    public void setStreakDay(int streakDay) { this.streakDay = streakDay; }
    public boolean isRewarded() { return rewarded; }
    public void setRewarded(boolean rewarded) { this.rewarded = rewarded; }
    public Long getRewardCouponId() { return rewardCouponId; }
    public void setRewardCouponId(Long rewardCouponId) { this.rewardCouponId = rewardCouponId; }
    public Long getReservationOrderId() { return reservationOrderId; }
    public void setReservationOrderId(Long reservationOrderId) { this.reservationOrderId = reservationOrderId; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    public static class EligibleOrder {
        private Long orderId;
        private String seatNo;
        private String slotDate;
        private String startTime;
        private String endTime;
        private boolean checkinable;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getSeatNo() { return seatNo; }
        public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
        public String getSlotDate() { return slotDate; }
        public void setSlotDate(String slotDate) { this.slotDate = slotDate; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public boolean isCheckinable() { return checkinable; }
        public void setCheckinable(boolean checkinable) { this.checkinable = checkinable; }
    }

    public static class EligibleResponse {
        private Long venueId;
        private String venueName;
        private boolean todayCheckedIn;
        private int streak;
        private List<EligibleOrder> orders;

        public Long getVenueId() { return venueId; }
        public void setVenueId(Long venueId) { this.venueId = venueId; }
        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }
        public boolean isTodayCheckedIn() { return todayCheckedIn; }
        public void setTodayCheckedIn(boolean todayCheckedIn) { this.todayCheckedIn = todayCheckedIn; }
        public int getStreak() { return streak; }
        public void setStreak(int streak) { this.streak = streak; }
        public List<EligibleOrder> getOrders() { return orders; }
        public void setOrders(List<EligibleOrder> orders) { this.orders = orders; }
    }

    public static class CalendarResponse {
        private String month;
        private List<Integer> days;
        private int streak;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public List<Integer> getDays() { return days; }
        public void setDays(List<Integer> days) { this.days = days; }
        public int getStreak() { return streak; }
        public void setStreak(int streak) { this.streak = streak; }
    }

    public static class VenueCodeResponse {
        private Long venueId;
        private String venueName;
        private String code;
        private String checkinUrl;
        private String expireAt;

        public Long getVenueId() { return venueId; }
        public void setVenueId(Long venueId) { this.venueId = venueId; }
        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getCheckinUrl() { return checkinUrl; }
        public void setCheckinUrl(String checkinUrl) { this.checkinUrl = checkinUrl; }
        public String getExpireAt() { return expireAt; }
        public void setExpireAt(String expireAt) { this.expireAt = expireAt; }
    }
}
