package com.zx.marketing.checkin.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class CheckinException extends BusinessException {

    public CheckinException(int code, String message) {
        super(code, message);
    }

    public static CheckinException alreadyCheckedInToday() {
        return new CheckinException(ErrorCode.CHECKIN_ALREADY_CHECKED_IN_TODAY, "今日已签到");
    }

    public static CheckinException noEligibleReservation() {
        return new CheckinException(ErrorCode.CHECKIN_NO_ELIGIBLE_RESERVATION, "无有效预约，不可签到");
    }

    public static CheckinException dateMismatch() {
        return new CheckinException(ErrorCode.CHECKIN_DATE_MISMATCH, "预约日期与今日不匹配");
    }

    public static CheckinException orderAlreadyCheckedIn() {
        return new CheckinException(ErrorCode.CHECKIN_ORDER_ALREADY_CHECKED_IN, "该预约已签到");
    }

    public static CheckinException outsideCheckinWindow() {
        return new CheckinException(ErrorCode.CHECKIN_OUTSIDE_WINDOW, "不在可签到时间段");
    }

    public static CheckinException orderNotFound() {
        return new CheckinException(ErrorCode.CHECKIN_ORDER_NOT_FOUND, "预约单不存在");
    }

    public static CheckinException invalidVenueCode() {
        return new CheckinException(ErrorCode.CHECKIN_INVALID_VENUE_CODE, "场馆签到码无效或已过期");
    }

    public static CheckinException venueMismatch() {
        return new CheckinException(ErrorCode.CHECKIN_VENUE_MISMATCH, "预约场馆与扫码场馆不一致");
    }

    public static CheckinException forbidden() {
        return new CheckinException(ErrorCode.CHECKIN_FORBIDDEN, "无权操作他人预约单");
    }
}
