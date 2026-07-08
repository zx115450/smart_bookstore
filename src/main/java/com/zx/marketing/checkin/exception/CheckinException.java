package com.zx.marketing.checkin.exception;

public class CheckinException extends RuntimeException {

    private final int code;

    public CheckinException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CheckinException alreadyCheckedInToday() {
        return new CheckinException(4301, "今日已签到");
    }

    public static CheckinException noEligibleReservation() {
        return new CheckinException(4302, "无有效预约，不可签到");
    }

    public static CheckinException dateMismatch() {
        return new CheckinException(4303, "预约日期与今日不匹配");
    }

    public static CheckinException orderAlreadyCheckedIn() {
        return new CheckinException(4304, "该预约已签到");
    }

    public static CheckinException invalidVenueCode() {
        return new CheckinException(4310, "场馆签到码无效或已过期");
    }

    public static CheckinException venueMismatch() {
        return new CheckinException(4311, "预约场馆与扫码场馆不一致");
    }

    public static CheckinException outsideCheckinWindow() {
        return new CheckinException(4308, "不在可签到时间段");
    }

    public static CheckinException orderNotFound() {
        return new CheckinException(4309, "预约单不存在");
    }

    public static CheckinException forbidden() {
        return new CheckinException(4312, "无权操作他人预约单");
    }
}
