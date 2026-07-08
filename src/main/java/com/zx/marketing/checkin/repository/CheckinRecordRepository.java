package com.zx.marketing.checkin.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.marketing.checkin.entity.CheckinRecord;
import com.zx.marketing.checkin.mapper.CheckinRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CheckinRecordRepository {

    private final CheckinRecordMapper mapper;

    public Optional<CheckinRecord> findByUserAndDate(Long userId, LocalDate date) {
        if (userId == null || date == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getUserId, userId)
                        .eq(CheckinRecord::getCheckinDate, date)
        ));
    }

    public CheckinRecord save(CheckinRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getId() == null) {
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(now);
            }
            mapper.insert(record);
            return record;
        }
        mapper.updateById(record);
        return record;
    }
}
