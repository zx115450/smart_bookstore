package com.zx.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.reservation.entity.ReservationOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface ReservationOrderMapper extends BaseMapper<ReservationOrder> {

    @Select("""
            SELECT COUNT(*)
            FROM reservation_order o
            INNER JOIN reservation_time_slot s ON s.id = o.time_slot_id
            WHERE o.user_id = #{userId}
              AND s.slot_date = #{slotDate}
              AND o.status IN ('BOOKED', 'COMPLETED')
            """)
    long countActiveOrdersByUserAndDate(@Param("userId") Long userId, @Param("slotDate") LocalDate slotDate);

    @Update("""
            UPDATE reservation_order
            SET checkin_at = NOW(), updated_at = NOW()
            WHERE id = #{id} AND status = 'BOOKED' AND checkin_at IS NULL
            """)
    int updateCheckinAt(@Param("id") Long id);
}
