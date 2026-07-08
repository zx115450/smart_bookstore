package com.zx.marketing.checkin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinRedisService {

    private static final String PREFIX_VENUE_CODE = "venue:checkin:code:";
    private static final String PREFIX_BITMAP = "sign:user:";
    private static final String PREFIX_STREAK = "sign:streak:";
    private static final String PREFIX_LAST = "sign:last:";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public String getOrCreateDailyVenueCode(Long venueId, LocalDate date) {
        String key = venueCodeKey(venueId, date);
        String existing = redis.opsForValue().get(key);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String code = randomCode(6);
        redis.opsForValue().set(key, code, ttlUntilEndOfDay(date));
        return code;
    }

    public boolean validateDailyVenueCode(Long venueId, LocalDate date, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String expected = redis.opsForValue().get(venueCodeKey(venueId, date));
        return expected != null && expected.equalsIgnoreCase(code.trim());
    }

    public void markCheckedIn(Long userId, LocalDate date) {
        String monthKey = bitmapKey(userId, date);
        int offset = date.getDayOfMonth() - 1;
        redis.opsForValue().setBit(monthKey, offset, true);
        redis.expire(monthKey, Duration.ofDays(40));
    }

    public List<Integer> getMonthlyCalendar(Long userId, LocalDate month) {
        String monthKey = bitmapKey(userId, month);
        int daysInMonth = month.lengthOfMonth();
        List<Integer> result = new ArrayList<>(daysInMonth);
        for (int day = 1; day <= daysInMonth; day++) {
            Boolean bit = redis.opsForValue().getBit(monthKey, day - 1);
            result.add(Boolean.TRUE.equals(bit) ? 1 : 0);
        }
        return result;
    }

    public int updateStreak(Long userId, LocalDate today) {
        String last = redis.opsForValue().get(lastKey(userId));
        String yesterday = today.minusDays(1).format(DAY_FMT);
        int streak;
        if (yesterday.equals(last)) {
            Long val = redis.opsForValue().increment(streakKey(userId));
            streak = val == null ? 1 : val.intValue();
        } else {
            redis.opsForValue().set(streakKey(userId), "1");
            streak = 1;
        }
        redis.opsForValue().set(lastKey(userId), today.format(DAY_FMT));
        return streak;
    }

    public int getStreak(Long userId) {
        String val = redis.opsForValue().get(streakKey(userId));
        if (val == null || val.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void resetStreak(Long userId) {
        redis.opsForValue().set(streakKey(userId), "0");
    }

    public boolean hasCheckedInToday(Long userId, LocalDate today) {
        String monthKey = bitmapKey(userId, today);
        return Boolean.TRUE.equals(redis.opsForValue().getBit(monthKey, today.getDayOfMonth() - 1));
    }

    private String venueCodeKey(Long venueId, LocalDate date) {
        return PREFIX_VENUE_CODE + venueId + ":" + date;
    }

    private String bitmapKey(Long userId, LocalDate date) {
        return PREFIX_BITMAP + userId + ":" + date.format(MONTH_FMT);
    }

    private String streakKey(Long userId) {
        return PREFIX_STREAK + userId;
    }

    private String lastKey(Long userId) {
        return PREFIX_LAST + userId;
    }

    private Duration ttlUntilEndOfDay(LocalDate date) {
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Duration ttl = Duration.between(LocalDateTime.now(), end);
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofHours(1);
        }
        return ttl.plusHours(1);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
