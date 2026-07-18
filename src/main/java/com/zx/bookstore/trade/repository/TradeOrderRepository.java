package com.zx.bookstore.trade.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.trade.entity.TradeOrder;
import com.zx.bookstore.trade.entity.TradeOrderItem;
import com.zx.bookstore.trade.mapper.TradeOrderItemMapper;
import com.zx.bookstore.trade.mapper.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TradeOrderRepository {

    private final TradeOrderMapper orderMapper;
    private final TradeOrderItemMapper itemMapper;

    public Optional<TradeOrder> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectById(id));
    }

    public Optional<TradeOrder> findByIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(
                Wrappers.<TradeOrder>lambdaQuery().eq(TradeOrder::getIdempotencyKey, key)
        ));
    }

    public TradeOrder save(TradeOrder order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getId() == null) {
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(now);
            }
            order.setUpdatedAt(now);
            orderMapper.insert(order);
            return order;
        }
        order.setUpdatedAt(now);
        orderMapper.updateById(order);
        return order;
    }

    public TradeOrderItem saveItem(TradeOrderItem item) {
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }
        itemMapper.insert(item);
        return item;
    }

    public List<TradeOrderItem> findItemsByOrderId(Long orderId) {
        return itemMapper.selectList(
                Wrappers.<TradeOrderItem>lambdaQuery().eq(TradeOrderItem::getOrderId, orderId)
        );
    }

    public List<TradeOrder> pageByUser(Long userId, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getUserId, userId)
                .orderByDesc(TradeOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        return orderMapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countByUser(Long userId, String status) {
        var wrapper = Wrappers.<TradeOrder>lambdaQuery().eq(TradeOrder::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        Long count = orderMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public boolean markPaid(Long orderId, Long userId) {
        return orderMapper.markPaid(orderId, userId) > 0;
    }

    public boolean markCancelled(Long orderId, Long userId) {
        return orderMapper.markCancelled(orderId, userId) > 0;
    }

    public boolean markCancelledByTimeout(Long orderId) {
        return orderMapper.markCancelledByTimeout(orderId) > 0;
    }

    public List<TradeOrder> pageAll(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .orderByDesc(TradeOrder::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        return orderMapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
    }

    public long countAll(String status) {
        var wrapper = Wrappers.<TradeOrder>lambdaQuery();
        if (status != null && !status.isBlank()) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        Long count = orderMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    /** 推荐热度：按 book_id 聚合已支付订单的商品销量（SUM quantity），TopN。 */
    public List<Map<String, Object>> findHotPaidBooks(int limit) {
        int safe = Math.min(Math.max(limit, 1), 200);
        return itemMapper.findHotPaidBooks(safe);
    }
}
