package com.zx.reservation.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reservation.entity.ReservationOrder;
import com.zx.reservation.enums.ReservationOrderStatus;
import com.zx.reservation.mapper.ReservationOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReservationOrderRepository {

    private final ReservationOrderMapper mapper;

    public Optional<ReservationOrder> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<ReservationOrder> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .eq(ReservationOrder::getIdempotencyKey, key)
        ));
    }

    public boolean existsBookedByUserAndSlot(Long userId, Long slotId) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .eq(ReservationOrder::getUserId, userId)
                        .eq(ReservationOrder::getTimeSlotId, slotId)
                        .eq(ReservationOrder::getStatus, ReservationOrderStatus.BOOKED.name())
        );
        return count != null && count > 0;
    }

    public boolean existsBookedBySlotAndSeat(Long slotId, Long seatId) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .eq(ReservationOrder::getTimeSlotId, slotId)
                        .eq(ReservationOrder::getSeatId, seatId)
                        .eq(ReservationOrder::getStatus, ReservationOrderStatus.BOOKED.name())
        );
        return count != null && count > 0;
    }

    public List<Long> findBookedSeatIdsBySlot(Long slotId) {
        return mapper.selectList(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .select(ReservationOrder::getSeatId)
                        .eq(ReservationOrder::getTimeSlotId, slotId)
                        .eq(ReservationOrder::getStatus, ReservationOrderStatus.BOOKED.name())
        ).stream().map(ReservationOrder::getSeatId).toList();
    }

    public long countBookedSeatsBySlot(Long slotId) {
        Long count = mapper.selectCount(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .eq(ReservationOrder::getTimeSlotId, slotId)
                        .eq(ReservationOrder::getStatus, ReservationOrderStatus.BOOKED.name())
        );
        return count == null ? 0 : count;
    }

    public long countActiveOrdersByUserAndDate(Long userId, LocalDate date) {
        return mapper.countActiveOrdersByUserAndDate(userId, date);
    }

    public List<ReservationOrder> pageByUser(Long userId, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<ReservationOrder>lambdaQuery()
                .eq(ReservationOrder::getUserId, userId)
                .orderByDesc(ReservationOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReservationOrder::getStatus, status);
        }
        return mapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countByUser(Long userId, String status) {
        var wrapper = Wrappers.<ReservationOrder>lambdaQuery().eq(ReservationOrder::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReservationOrder::getStatus, status);
        }
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public List<ReservationOrder> pageAll(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<ReservationOrder>lambdaQuery()
                .orderByDesc(ReservationOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReservationOrder::getStatus, status);
        }
        return mapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countAll(String status) {
        var wrapper = Wrappers.<ReservationOrder>lambdaQuery();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReservationOrder::getStatus, status);
        }
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public List<ReservationOrder> findBookedCheckinableByUserAndResource(Long userId, Long resourceId) {
        return mapper.selectList(
                Wrappers.<ReservationOrder>lambdaQuery()
                        .eq(ReservationOrder::getUserId, userId)
                        .eq(ReservationOrder::getResourceId, resourceId)
                        .eq(ReservationOrder::getStatus, ReservationOrderStatus.BOOKED.name())
                        .isNull(ReservationOrder::getCheckinAt)
                        .orderByAsc(ReservationOrder::getId)
        );
    }

    public boolean updateCheckinAt(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return mapper.updateCheckinAt(orderId) > 0;
    }

    public ReservationOrder save(ReservationOrder order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getId() == null) {
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(now);
            }
            order.setUpdatedAt(now);
            mapper.insert(order);
            return order;
        }
        order.setUpdatedAt(now);
        mapper.updateById(order);
        return order;
    }
}
