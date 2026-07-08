package com.zx.reservation.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reservation.entity.ReservationTimeSlot;
import com.zx.reservation.mapper.ReservationTimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationTimeSlotRepository {

    private final ReservationTimeSlotMapper mapper;

    public Optional<ReservationTimeSlot> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<ReservationTimeSlot> listByResourceAndDate(Long resourceId, LocalDate date) {
        return mapper.selectList(
                Wrappers.<ReservationTimeSlot>lambdaQuery()
                        .eq(ReservationTimeSlot::getResourceId, resourceId)
                        .eq(ReservationTimeSlot::getSlotDate, date)
                        .orderByAsc(ReservationTimeSlot::getStartTime)
        );
    }

    public boolean tryReserve(Long slotId) {
        return mapper.tryReserve(slotId) > 0;
    }

    public boolean release(Long slotId) {
        return mapper.release(slotId) > 0;
    }

    public ReservationTimeSlot save(ReservationTimeSlot slot) {
        LocalDateTime now = LocalDateTime.now();
        if (slot.getId() == null) {
            if (slot.getCreatedAt() == null) {
                slot.setCreatedAt(now);
            }
            slot.setUpdatedAt(now);
            mapper.insert(slot);
            return slot;
        }
        slot.setUpdatedAt(now);
        mapper.updateById(slot);
        return slot;
    }

    public boolean existsByResourceDateStart(Long resourceId, LocalDate date, java.time.LocalTime startTime) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationTimeSlot>lambdaQuery()
                        .eq(ReservationTimeSlot::getResourceId, resourceId)
                        .eq(ReservationTimeSlot::getSlotDate, date)
                        .eq(ReservationTimeSlot::getStartTime, startTime)
        );
        return count != null && count > 0;
    }
}
