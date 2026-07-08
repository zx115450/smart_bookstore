package com.zx.marketing.checkin.repository;

import com.zx.marketing.checkin.entity.CheckinStreak;
import com.zx.marketing.checkin.mapper.CheckinStreakMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CheckinStreakRepository {

    private final CheckinStreakMapper mapper;

    public Optional<CheckinStreak> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(userId));
    }

    public CheckinStreak save(CheckinStreak streak) {
        LocalDateTime now = LocalDateTime.now();
        streak.setUpdatedAt(now);
        if (streak.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (mapper.selectById(streak.getUserId()) == null) {
            if (streak.getCurrentStreak() == null) {
                streak.setCurrentStreak(0);
            }
            if (streak.getTotalCheckins() == null) {
                streak.setTotalCheckins(0);
            }
            mapper.insert(streak);
        } else {
            mapper.updateById(streak);
        }
        return streak;
    }
}
