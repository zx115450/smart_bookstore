package com.zx.reservation.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class ReservationException extends BusinessException {

    public ReservationException(int code, String message) {
        super(code, message);
    }

    public static ReservationException slotNotFound() {
        return new ReservationException(ErrorCode.RESERVATION_SLOT_NOT_FOUND, "时段不存在或已关闭");
    }

    public static ReservationException noCapacity() {
        return new ReservationException(ErrorCode.RESERVATION_NO_CAPACITY, "名额不足");
    }

    public static ReservationException duplicateBooking() {
        return new ReservationException(ErrorCode.RESERVATION_DUPLICATE_BOOKING, "您已预约该时段");
    }

    public static ReservationException invalidStatus() {
        return new ReservationException(ErrorCode.RESERVATION_INVALID_STATUS, "当前状态不允许该操作");
    }

    public static ReservationException forbidden() {
        return new ReservationException(ErrorCode.RESERVATION_FORBIDDEN, "无权操作他人预约");
    }

    public static ReservationException dailyLimitExceeded() {
        return new ReservationException(ErrorCode.RESERVATION_DAILY_LIMIT_EXCEEDED, "超过每日预约上限");
    }

    public static ReservationException resourceNotFound() {
        return new ReservationException(ErrorCode.RESERVATION_RESOURCE_NOT_FOUND, "自习室不存在或已下架");
    }

    public static ReservationException seatNotFound() {
        return new ReservationException(ErrorCode.RESERVATION_SEAT_NOT_FOUND, "座位不存在或已停用");
    }

    public static ReservationException seatAlreadyBooked() {
        return new ReservationException(ErrorCode.RESERVATION_SEAT_ALREADY_BOOKED, "该座位在该时段已被预约");
    }

    public static ReservationException orderNotFound() {
        return new ReservationException(ErrorCode.RESERVATION_ORDER_NOT_FOUND, "预约单不存在");
    }

    public static ReservationException slotExpired() {
        return new ReservationException(ErrorCode.RESERVATION_SLOT_EXPIRED, "时段已过期，无法预约");
    }
}
