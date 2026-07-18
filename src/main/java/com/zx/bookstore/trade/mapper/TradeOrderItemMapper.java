package com.zx.bookstore.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.trade.entity.TradeOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TradeOrderItemMapper extends BaseMapper<TradeOrderItem> {

    @Select("""
            SELECT i.book_id AS bookId, SUM(i.quantity) AS heat
            FROM trade_order_item i
            JOIN trade_order o ON i.order_id = o.id
            WHERE o.status = 'PAID'
            GROUP BY i.book_id
            ORDER BY heat DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findHotPaidBooks(@Param("limit") int limit);
}
