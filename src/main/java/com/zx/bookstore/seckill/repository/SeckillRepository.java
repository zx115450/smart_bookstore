package com.zx.bookstore.seckill.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.seckill.entity.SeckillActivity;
import com.zx.bookstore.seckill.entity.SeckillOrder;
import com.zx.bookstore.seckill.mapper.SeckillActivityMapper;
import com.zx.bookstore.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 秒杀活动与参与记录持久层。 */
@Repository
@RequiredArgsConstructor
public class SeckillRepository {

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;

    public Optional<SeckillActivity> findActivityById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activityMapper.selectById(id));
    }

    /** 用户端列表：启用且未结束的活动（进行中 + 即将开始）。 */
    public List<SeckillActivity> listVisibleActivities() {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(
                Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(SeckillActivity::getStatus, 1)
                        .ge(SeckillActivity::getEndTime, now)
                        .orderByAsc(SeckillActivity::getStartTime)
        );
    }

    public List<SeckillActivity> listAllActivities() {
        return activityMapper.selectList(
                Wrappers.<SeckillActivity>lambdaQuery()
                        .orderByDesc(SeckillActivity::getId)
        );
    }

    public SeckillActivity saveActivity(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (activity.getId() == null) {
            if (activity.getCreatedAt() == null) {
                activity.setCreatedAt(now);
            }
            activity.setUpdatedAt(now);
            activityMapper.insert(activity);
            return activity;
        }
        activity.setUpdatedAt(now);
        activityMapper.updateById(activity);
        return activity;
    }

    public Optional<SeckillOrder> findOrderByUserAndActivity(Long userId, Long activityId) {
        if (userId == null || activityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery()
                        .eq(SeckillOrder::getUserId, userId)
                        .eq(SeckillOrder::getActivityId, activityId)
        ));
    }

    public Optional<SeckillOrder> findOrderByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(orderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery()
                        .eq(SeckillOrder::getIdempotencyKey, idempotencyKey)
        ));
    }

    public SeckillOrder saveOrder(SeckillOrder order) {
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
}
