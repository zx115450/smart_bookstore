package com.zx.reservation.exception;

public class ReservationException extends RuntimeException {

    private final int code;

    public ReservationException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ReservationException slotNotFound() {
        return new ReservationException(3001, "时段不存在或已关闭");
    }

    public static ReservationException noCapacity() {
        return new ReservationException(3002, "名额不足");
    }

    public static ReservationException duplicateBooking() {
        return new ReservationException(3003, "您已预约该时段");
    }

    public static ReservationException invalidStatus() {
        return new ReservationException(3004, "当前状态不允许该操作");
    }

    public static ReservationException forbidden() {
        return new ReservationException(3005, "无权操作他人预约");
    }

    public static ReservationException dailyLimitExceeded() {
        return new ReservationException(3006, "超过每日预约上限");
    }

    public static ReservationException resourceNotFound() {
        return new ReservationException(3007, "自习室不存在或已下架");
    }

    public static ReservationException seatNotFound() {
        return new ReservationException(3010, "座位不存在或已停用");
    }

    public static ReservationException seatAlreadyBooked() {
        return new ReservationException(3011, "该座位在该时段已被预约");
    }

    public static ReservationException orderNotFound() {
        return new ReservationException(3008, "预约单不存在");
    }

    public static ReservationException slotExpired() {
        return new ReservationException(3009, "时段已过期，无法预约");
    }
}
