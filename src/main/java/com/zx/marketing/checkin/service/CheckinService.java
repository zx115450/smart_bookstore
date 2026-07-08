package com.zx.marketing.checkin.service;

import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.coupon.entity.UserCoupon;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.marketing.checkin.config.CheckinProperties;
import com.zx.marketing.checkin.dto.CheckinRequest;
import com.zx.marketing.checkin.dto.CheckinResponse;
import com.zx.marketing.checkin.entity.CheckinRecord;
import com.zx.marketing.checkin.entity.CheckinStreak;
import com.zx.marketing.checkin.exception.CheckinException;
import com.zx.marketing.checkin.repository.CheckinRecordRepository;
import com.zx.marketing.checkin.repository.CheckinStreakRepository;
import com.zx.reservation.entity.ReservationOrder;
import com.zx.reservation.entity.ReservationResource;
import com.zx.reservation.entity.ReservationTimeSlot;
import com.zx.reservation.enums.ReservationOrderStatus;
import com.zx.reservation.exception.ReservationException;
import com.zx.reservation.repository.ReservationOrderRepository;
import com.zx.reservation.repository.ReservationResourceRepository;
import com.zx.reservation.repository.ReservationSeatRepository;
import com.zx.reservation.repository.ReservationTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CheckinProperties properties;
    private final CheckinRedisService checkinRedisService;
    private final CheckinRecordRepository checkinRecordRepository;
    private final CheckinStreakRepository checkinStreakRepository;
    private final ReservationOrderRepository orderRepository;
    private final ReservationResourceRepository resourceRepository;
    private final ReservationTimeSlotRepository slotRepository;
    private final ReservationSeatRepository seatRepository;
    private final CouponService couponService;

    public CheckinResponse.VenueCodeResponse getDailyVenueCode(Long venueId) {
        ReservationResource resource = resourceRepository.findById(venueId)
                .orElseThrow(ReservationException::resourceNotFound);
        LocalDate today = LocalDate.now();
        String code = checkinRedisService.getOrCreateDailyVenueCode(venueId, today);
        CheckinResponse.VenueCodeResponse resp = new CheckinResponse.VenueCodeResponse();
        resp.setVenueId(venueId);
        resp.setVenueName(resource.getName());
        resp.setCode(code);
        resp.setCheckinUrl(properties.getFrontendBaseUrl()
                + "/checkin?venueId=" + venueId + "&code=" + code);
        resp.setExpireAt(today.plusDays(1).atStartOfDay().format(DATETIME_FMT));
        return resp;
    }

    public CheckinResponse.EligibleResponse listEligible(
            AuthPrincipal principal, Long venueId, String code
    ) {
        validateVenueCode(venueId, code);
        ReservationResource resource = resourceRepository.findById(venueId)
                .orElseThrow(ReservationException::resourceNotFound);
        LocalDate today = LocalDate.now();

        CheckinResponse.EligibleResponse resp = new CheckinResponse.EligibleResponse();
        resp.setVenueId(venueId);
        resp.setVenueName(resource.getName());
        resp.setTodayCheckedIn(checkinRecordRepository.findByUserAndDate(principal.userId(), today).isPresent());
        resp.setStreak(checkinRedisService.getStreak(principal.userId()));

        List<CheckinResponse.EligibleOrder> orders = new ArrayList<>();
        for (ReservationOrder order : orderRepository.findBookedCheckinableByUserAndResource(principal.userId(), venueId)) {
            ReservationTimeSlot slot = slotRepository.findById(order.getTimeSlotId()).orElse(null);
            if (slot == null || !today.equals(slot.getSlotDate())) {
                continue;
            }
            CheckinResponse.EligibleOrder item = new CheckinResponse.EligibleOrder();
            item.setOrderId(order.getId());
            item.setSlotDate(slot.getSlotDate().format(DATE_FMT));
            item.setStartTime(slot.getStartTime().format(TIME_FMT));
            item.setEndTime(slot.getEndTime().format(TIME_FMT));
            seatRepository.findById(order.getSeatId()).ifPresent(seat -> item.setSeatNo(seat.getSeatNo()));
            item.setCheckinable(isWithinCheckinWindow(slot));
            orders.add(item);
        }
        resp.setOrders(orders);
        return resp;
    }

    @Transactional
    public CheckinResponse checkin(AuthPrincipal principal, CheckinRequest req) {
        /*
        public class CheckinRequest {
            private Long reservationOrderId;
            private Long venueId;
            private String code;
        }
         */

        if (req.getReservationOrderId() == null || req.getVenueId() == null) {
            throw CheckinException.noEligibleReservation();
        }
        //校验签到码
        validateVenueCode(req.getVenueId(), req.getCode());

        LocalDate today = LocalDate.now();
        //查看是否已经签到
        if (checkinRecordRepository.findByUserAndDate(principal.userId(), today).isPresent()) {
            throw CheckinException.alreadyCheckedInToday();
        }

        ReservationOrder order = orderRepository.findById(req.getReservationOrderId())
                .orElseThrow(CheckinException::orderNotFound);
        if (!principal.userId().equals(order.getUserId())) {
            throw CheckinException.forbidden();
        }
        //检验位置是否被当前user预定
        if (!ReservationOrderStatus.BOOKED.name().equals(order.getStatus())) {
            throw CheckinException.noEligibleReservation();
        }
        if (order.getCheckinAt() != null) {
            throw CheckinException.orderAlreadyCheckedIn();
        }
        if (!req.getVenueId().equals(order.getResourceId())) {
            throw CheckinException.venueMismatch();
        }

        ReservationTimeSlot slot = slotRepository.findById(order.getTimeSlotId())
                .orElseThrow(CheckinException::noEligibleReservation);
        if (!today.equals(slot.getSlotDate())) {
            throw CheckinException.dateMismatch();
        }
        if (!isWithinCheckinWindow(slot)) {
            throw CheckinException.outsideCheckinWindow();
        }

        if (!orderRepository.updateCheckinAt(order.getId())) {
            throw CheckinException.orderAlreadyCheckedIn();
        }

        int streakDay = checkinRedisService.updateStreak(principal.userId(), today);
        //更新bitmap
        checkinRedisService.markCheckedIn(principal.userId(), today);

        Long rewardCouponId = null;
        boolean rewarded = false;
        if (streakDay >= properties.getStreakRewardDays()) {
            UserCoupon coupon = couponService.issueCheckin7Coupon(principal.userId());
            rewardCouponId = coupon.getId();
            rewarded = true;
            checkinRedisService.resetStreak(principal.userId());
            streakDay = properties.getStreakRewardDays();
        }

        CheckinRecord record = new CheckinRecord();
        record.setUserId(principal.userId());
        record.setCheckinDate(today);
        record.setReservationOrderId(order.getId());
        record.setVenueId(req.getVenueId());
        record.setStreakDay(streakDay);
        record.setRewardCouponId(rewardCouponId);
        checkinRecordRepository.save(record);

        syncStreakToDb(principal.userId(), today, streakDay, rewarded);

        ReservationResource resource = resourceRepository.findById(req.getVenueId())
                .orElse(null);

        CheckinResponse resp = new CheckinResponse();
        resp.setCheckinDate(today.format(DATE_FMT));
        resp.setStreakDay(streakDay);
        resp.setStreak(rewarded ? 0 : checkinRedisService.getStreak(principal.userId()));
        resp.setRewarded(rewarded);
        resp.setRewardCouponId(rewardCouponId);
        resp.setReservationOrderId(order.getId());
        if (resource != null) {
            resp.setVenueName(resource.getName());
        }
        return resp;
    }

    public CheckinResponse.CalendarResponse getCalendar(AuthPrincipal principal, String month) {
        YearMonth ym = month == null || month.isBlank()
                ? YearMonth.now()
                : YearMonth.parse(month, MONTH_FMT);
        LocalDate firstDay = ym.atDay(1);
        CheckinResponse.CalendarResponse resp = new CheckinResponse.CalendarResponse();
        resp.setMonth(ym.format(MONTH_FMT));
        resp.setDays(checkinRedisService.getMonthlyCalendar(principal.userId(), firstDay));
        resp.setStreak(checkinRedisService.getStreak(principal.userId()));
        return resp;
    }

    public CheckinResponse getStreak(AuthPrincipal principal) {
        LocalDate today = LocalDate.now();
        CheckinResponse resp = new CheckinResponse();
        resp.setStreak(checkinRedisService.getStreak(principal.userId()));
        resp.setCheckinDate(today.format(DATE_FMT));
        resp.setStreakDay(resp.getStreak());
        return resp;
    }

    private void validateVenueCode(Long venueId, String code) {
        if (!checkinRedisService.validateDailyVenueCode(venueId, LocalDate.now(), code)) {
            throw CheckinException.invalidVenueCode();
        }
    }

    private boolean isWithinCheckinWindow(ReservationTimeSlot slot) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime())
                .minusMinutes(properties.getWindowMinutesBefore());
        LocalDateTime end = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime())
                .plusMinutes(properties.getWindowMinutesAfter());
        return !now.isBefore(start) && !now.isAfter(end);
    }

    private void syncStreakToDb(Long userId, LocalDate today, int streakDay, boolean rewarded) {
        CheckinStreak streak = checkinStreakRepository.findByUserId(userId).orElseGet(() -> {
            CheckinStreak s = new CheckinStreak();
            s.setUserId(userId);
            s.setCurrentStreak(0);
            s.setTotalCheckins(0);
            return s;
        });
        streak.setLastCheckinDate(today);
        streak.setTotalCheckins(streak.getTotalCheckins() + 1);
        streak.setCurrentStreak(rewarded ? 0 : streakDay);
        checkinStreakRepository.save(streak);
    }
}
