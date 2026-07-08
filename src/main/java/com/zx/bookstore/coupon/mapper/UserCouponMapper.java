package com.zx.bookstore.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT * FROM user_coupon WHERE id = #{id} FOR UPDATE")
    UserCoupon selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE user_coupon
            SET status = 'USED', used_at = NOW(), trade_order_id = #{tradeOrderId}, updated_at = NOW()
            WHERE id = #{id} AND status = 'UNUSED' AND user_id = #{userId}
            """)
    int markUsed(@Param("id") Long id, @Param("userId") Long userId, @Param("tradeOrderId") Long tradeOrderId);
}
