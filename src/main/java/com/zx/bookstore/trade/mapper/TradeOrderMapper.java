package com.zx.bookstore.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.trade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Update("""
            UPDATE trade_order
            SET status = 'PAID', paid_at = NOW(), updated_at = NOW()
            WHERE id = #{id} AND status = 'PENDING_PAY' AND user_id = #{userId}
            """)
    int markPaid(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE trade_order
            SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW()
            WHERE id = #{id} AND status = 'PENDING_PAY' AND user_id = #{userId}
            """)
    int markCancelled(@Param("id") Long id, @Param("userId") Long userId);
}
