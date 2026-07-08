package com.zx.reservation.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reservation.entity.ReservationSeat;
import com.zx.reservation.mapper.ReservationSeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationSeatRepository {

    private final ReservationSeatMapper mapper;

    public Optional<ReservationSeat> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<ReservationSeat> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectByIdForUpdate(id));
    }

    public Optional<ReservationSeat> findEnabledByIdAndResource(Long id, Long resourceId) {
        return findById(id)
                .filter(seat -> seat.getResourceId().equals(resourceId))
                .filter(seat -> seat.getStatus() != null && seat.getStatus() == 1);
    }

    public List<ReservationSeat> listEnabledByResource(Long resourceId) {
        return mapper.selectList(
                Wrappers.<ReservationSeat>lambdaQuery()
                        .eq(ReservationSeat::getResourceId, resourceId)
                        .eq(ReservationSeat::getStatus, 1)
                        .orderByAsc(ReservationSeat::getRowNum)
                        .orderByAsc(ReservationSeat::getColNum)
                        .orderByAsc(ReservationSeat::getSeatNo)
        );
    }

    public long countEnabledByResource(Long resourceId) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationSeat>lambdaQuery()
                        .eq(ReservationSeat::getResourceId, resourceId)
                        .eq(ReservationSeat::getStatus, 1)
        );
        return count == null ? 0 : count;
    }

    public boolean existsByResourceAndSeatNo(Long resourceId, String seatNo) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationSeat>lambdaQuery()
                        .eq(ReservationSeat::getResourceId, resourceId)
                        .eq(ReservationSeat::getSeatNo, seatNo)
        );
        return count != null && count > 0;
    }

    public ReservationSeat save(ReservationSeat seat) {
        LocalDateTime now = LocalDateTime.now();
        if (seat.getId() == null) {
            if (seat.getCreatedAt() == null) {
                seat.setCreatedAt(now);
            }
            seat.setUpdatedAt(now);
            mapper.insert(seat);
            return seat;
        }
        seat.setUpdatedAt(now);
        mapper.updateById(seat);
        return seat;
    }
}
