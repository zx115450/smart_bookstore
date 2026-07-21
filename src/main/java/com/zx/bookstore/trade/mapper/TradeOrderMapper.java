package com.zx.bookstore.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.trade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @Update("""
            UPDATE trade_order
            SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW()
            WHERE id = #{id} AND status = 'PENDING_PAY'
            """)
    int markCancelledByTimeout(@Param("id") Long id);

    @Select("""
            SELECT DATE(created_at) AS date, COUNT(*) AS orderCount, SUM(pay_amount) AS revenue
            FROM trade_order
            WHERE created_at >= #{start}
            GROUP BY DATE(created_at)
            ORDER BY date
            """)
    List<Map<String, Object>> revenueTrend(@Param("start") LocalDateTime start);

    @Select("""
            SELECT status, COUNT(*) AS cnt, SUM(pay_amount) AS amount
            FROM trade_order
            GROUP BY status
            """)
    List<Map<String, Object>> revenueByStatus();

    @Select("""
            SELECT COALESCE(SUM(pay_amount), 0) AS revenue,
                   COUNT(*) AS orderCount
            FROM trade_order
            WHERE DATE(created_at) = CURDATE()
            """)
    Map<String, Object> todayRevenue();
}
