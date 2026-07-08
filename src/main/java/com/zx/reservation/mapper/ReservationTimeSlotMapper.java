package com.zx.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.reservation.entity.ReservationTimeSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReservationTimeSlotMapper extends BaseMapper<ReservationTimeSlot> {

    /**
     * 原子预占名额：仅当 booked_count &lt; capacity 时 +1。
     *
     * @return 影响行数，0 表示已满或时段不存在
     */
    @Update("""
            UPDATE reservation_time_slot
            SET booked_count = booked_count + 1,
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id}
              AND booked_count < capacity
            """)
    int tryReserve(@Param("id") Long id);

    /**
     * 释放名额：仅当 booked_count &gt; 0 时 -1。
     */
    @Update("""
            UPDATE reservation_time_slot
            SET booked_count = booked_count - 1,
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id}
              AND booked_count > 0
            """)
    int release(@Param("id") Long id);
}
