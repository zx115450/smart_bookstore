package com.zx.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.reservation.entity.ReservationSeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReservationSeatMapper extends BaseMapper<ReservationSeat> {

    @Select("SELECT * FROM reservation_seat WHERE id = #{id} FOR UPDATE")
    ReservationSeat selectByIdForUpdate(@Param("id") Long id);
}
