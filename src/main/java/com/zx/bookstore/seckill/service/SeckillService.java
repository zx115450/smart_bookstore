package com.zx.bookstore.seckill.service;

import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.coupon.repository.CouponRepository;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.bookstore.seckill.dto.*;
import com.zx.bookstore.seckill.entity.SeckillActivity;
import com.zx.bookstore.seckill.entity.SeckillOrder;
import com.zx.bookstore.seckill.enums.SeckillOrderStatus;
import com.zx.bookstore.seckill.exception.SeckillException;
import com.zx.bookstore.seckill.repository.SeckillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * P4 秒杀核心业务：活动管理、Redis Lua 抢券、RabbitMQ 异步发券。
 * <p>
 * 抢券路径：grab → Lua 原子扣库存 → 发 MQ → 快速返回 PROCESSING；
 * 落库路径：Consumer 消费 → seckill_order + user_coupon（obtain_way=SECKILL）。
 * 一人一单由 Redis Set + DB uk_seckill_user_activity 双重保障。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SeckillRepository seckillRepository;
    private final SeckillRedisService seckillRedisService;
    private final SeckillMqProducer seckillMqProducer;
    private final CouponRepository couponRepository;
    private final CouponService couponService;

    public List<SeckillActivityResponse> listActivities() {
        return seckillRepository.listVisibleActivities().stream()
                .map(this::toActivityResponse)
                .toList();
    }

    public SeckillActivityResponse getActivity(Long id) {
        SeckillActivity activity = findEnabledActivity(id);
        return toActivityResponse(activity);
    }

    /**
     * 创建活动并预热 Redis 库存（管理端调用）。
     */
    @Transactional
    public SeckillActivityResponse createActivity(CreateSeckillActivityRequest req) {
        validateCreateRequest(req);
        couponRepository.findTemplateById(req.getTemplateId())
                .orElseThrow(() -> SeckillException.configError("优惠券模板不存在"));

        SeckillActivity activity = new SeckillActivity();
        activity.setName(req.getName().trim());
        activity.setTemplateId(req.getTemplateId());
        activity.setSeckillStock(req.getSeckillStock());
        activity.setStartTime(parseDateTime(req.getStartTime()));
        activity.setEndTime(parseDateTime(req.getEndTime()));
        activity.setStatus(1);
        seckillRepository.saveActivity(activity);

        seckillRedisService.warmUpStock(activity.getId(), activity.getSeckillStock(), activity.getEndTime());
        return toActivityResponse(activity);
    }

    public List<SeckillActivityResponse> listAllActivitiesAdmin() {
        return seckillRepository.listAllActivities().stream()
                .map(this::toActivityResponse)
                .toList();
    }

    public SeckillActivityResponse getActivityAdmin(Long id) {
        SeckillActivity activity = seckillRepository.findActivityById(id)
                .orElseThrow(SeckillException::activityNotFound);
        return toActivityResponse(activity);
    }

    @Transactional
    public SeckillActivityResponse updateActivity(Long id, UpdateSeckillActivityRequest req) {
        SeckillActivity activity = seckillRepository.findActivityById(id)
                .orElseThrow(SeckillException::activityNotFound);
        if (req == null) {
            return toActivityResponse(activity);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean ongoing = !now.isBefore(activity.getStartTime()) && !now.isAfter(activity.getEndTime());

        if (StringUtils.hasText(req.getName())) {
            activity.setName(req.getName().trim());
        }
        if (req.getTemplateId() != null) {
            couponRepository.findTemplateById(req.getTemplateId())
                    .orElseThrow(() -> SeckillException.configError("优惠券模板不存在"));
            activity.setTemplateId(req.getTemplateId());
        }
        if (StringUtils.hasText(req.getStartTime())) {
            activity.setStartTime(parseDateTime(req.getStartTime()));
        }
        if (StringUtils.hasText(req.getEndTime())) {
            activity.setEndTime(parseDateTime(req.getEndTime()));
        }
        if (activity.getEndTime() != null && activity.getStartTime() != null
                && !activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new IllegalArgumentException("endTime 必须晚于 startTime");
        }
        if (req.getSeckillStock() != null) {
            if (req.getSeckillStock() <= 0) {
                throw new IllegalArgumentException("seckillStock 必须大于 0");
            }
            if (ongoing) {
                throw new IllegalArgumentException("活动进行中不可修改库存，请先结束活动");
            }
            activity.setSeckillStock(req.getSeckillStock());
        }
        if (req.getStatus() != null) {
            activity.setStatus(req.getStatus());
        }

        seckillRepository.saveActivity(activity);
        if (activity.getStatus() != null && activity.getStatus() == 1
                && req.getSeckillStock() != null && !ongoing) {
            seckillRedisService.warmUpStock(activity.getId(), activity.getSeckillStock(), activity.getEndTime());
        }
        return toActivityResponse(activity);
    }

    @Transactional
    public void disableActivity(Long id) {
        SeckillActivity activity = seckillRepository.findActivityById(id)
                .orElseThrow(SeckillException::activityNotFound);
        if (activity.getStatus() != null && activity.getStatus() == 0) {
            return;
        }
        activity.setStatus(0);
        seckillRepository.saveActivity(activity);
    }

    /**
     * 抢券入口：幂等键重试 → Lua 原子判库存/判重复 → 发 MQ → 返回排队中。
     * Lua 成功后库存已扣，若 MQ 发送失败需回滚 Redis。
     */
    public SeckillGrabResponse grab(AuthPrincipal principal, Long activityId, SeckillGrabRequest req) {
        if (!StringUtils.hasText(req.getIdempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }

        SeckillActivity activity = findEnabledActivity(activityId);
        validateActivityWindow(activity);

        // 同 idempotencyKey 重试：直接返回已有订单状态，不重复执行 Lua
        var existingByKey = seckillRepository.findOrderByIdempotencyKey(req.getIdempotencyKey());
        if (existingByKey.isPresent()) {
            return toGrabResponse(activityId, existingByKey.get());
        }

        var existingByUser = seckillRepository.findOrderByUserAndActivity(principal.userId(), activityId);
        if (existingByUser.isPresent()) {
            SeckillOrder order = existingByUser.get();
            if (SeckillOrderStatus.PROCESSING.name().equals(order.getStatus())) {
                return toGrabResponse(activityId, order);
            }
            throw SeckillException.alreadyParticipated();
        }

        // Lua：0=售罄 1=成功 2=已参与
        Long luaResult = seckillRedisService.grab(activityId, principal.userId());
        if (luaResult == 0L) {
            throw SeckillException.soldOut();
        }
        if (luaResult == 2L) {
            throw SeckillException.alreadyParticipated();
        }

        SeckillOrderMessage message = new SeckillOrderMessage(
                principal.userId(), activityId, req.getIdempotencyKey());
        try {
            seckillMqProducer.publishSeckillOrder(message);
        } catch (Exception e) {
            log.error("publish seckill order failed, userId={}, activityId={}", principal.userId(), activityId, e);
            seckillRedisService.rollbackGrab(activityId, principal.userId());
            throw new SeckillException(4256, "系统繁忙，请稍后重试");
        }

        SeckillGrabResponse response = new SeckillGrabResponse();
        response.setActivityId(activityId);
        response.setStatus(SeckillOrderStatus.PROCESSING.name());
        response.setMessage("排队中，请稍后查询结果");
        return response;
    }

    /**
     * 轮询抢购结果。MQ 消费完成前 DB 可能尚无记录，此时查 Redis 用户 Set 判定 PROCESSING。
     */
    public SeckillResultResponse getResult(AuthPrincipal principal, Long activityId) {
        findEnabledActivity(activityId);
        var orderOpt = seckillRepository.findOrderByUserAndActivity(principal.userId(), activityId);
        if (orderOpt.isPresent()) {
            return toResultResponse(activityId, orderOpt.get());
        }
        // Lua 已成功但 Consumer 尚未落库：Redis 中用户已在 Set 内
        if (seckillRedisService.hasGrabbedUser(activityId, principal.userId())) {
            SeckillResultResponse response = new SeckillResultResponse();
            response.setActivityId(activityId);
            response.setStatus(SeckillOrderStatus.PROCESSING.name());
            return response;
        }
        throw new SeckillException(4257, "尚未参与该活动");
    }

    /**
     * MQ 消费端：幂等检查后写入 seckill_order，再同步发券。
     * 发券失败时标记 FAILED 并回滚 Redis，正常返回让主 Consumer ACK（不再抛出，避免事务回滚冲掉 FAILED）。
     * 早期异常（参数非法、活动不存在等）仍抛出 → Nack → 死信对账。
     */
    @Transactional
    public void processSeckillOrder(SeckillOrderMessage message) {
        if (message.getUserId() == null || message.getActivityId() == null
                || !StringUtils.hasText(message.getIdempotencyKey())) {
            throw new IllegalArgumentException("秒杀消息参数不完整");
        }

        var existingByKey = seckillRepository.findOrderByIdempotencyKey(message.getIdempotencyKey());
        if (existingByKey.isPresent()) {
            return;
        }

        var existingByUser = seckillRepository.findOrderByUserAndActivity(
                message.getUserId(), message.getActivityId());
        if (existingByUser.isPresent()) {
            return;
        }

        SeckillActivity activity = seckillRepository.findActivityById(message.getActivityId())
                .orElseThrow(() -> SeckillException.configError("秒杀活动不存在"));

        SeckillOrder order = new SeckillOrder();
        order.setUserId(message.getUserId());
        order.setActivityId(message.getActivityId());
        order.setStatus(SeckillOrderStatus.PROCESSING.name());
        order.setIdempotencyKey(message.getIdempotencyKey());

        // uk_seckill_user_activity / uk_seckill_idempotency 并发兜底
        try {
            seckillRepository.saveOrder(order);
        } catch (DuplicateKeyException e) {
            log.info("duplicate seckill order ignored, userId={}, activityId={}",
                    message.getUserId(), message.getActivityId());
            return;
        }

        try {
            var userCoupon = couponService.issueSeckillCoupon(message.getUserId(), activity.getTemplateId());
            order.setStatus(SeckillOrderStatus.SUCCESS.name());
            order.setUserCouponId(userCoupon.getId());
            seckillRepository.saveOrder(order);
        } catch (Exception e) {
            log.error("issue seckill coupon failed, userId={}, activityId={}",
                    message.getUserId(), message.getActivityId(), e);
            order.setStatus(SeckillOrderStatus.FAILED.name());
            order.setFailReason(truncate(e.getMessage()));
            seckillRepository.saveOrder(order);
            seckillRedisService.rollbackGrab(message.getActivityId(), message.getUserId());
            // 不抛出：保证 FAILED 落库提交，主 Consumer ACK；死信只兜早期失败
        }
    }

    /**
     * 死信对账：主队列 Nack 后的兜底，不重投主队列。
     * <ul>
     *   <li>已 SUCCESS：无需处理</li>
     *   <li>已 FAILED：确认 Redis 已回滚，否则补 rollback</li>
     *   <li>无订单但 Redis 仍占名额：rollbackGrab，避免库存泄漏</li>
     *   <li>卡在 PROCESSING：标 FAILED 并回滚 Redis</li>
     * </ul>
     */
    public void reconcileDeadLetter(SeckillOrderMessage message) {
        if (message == null || message.getUserId() == null || message.getActivityId() == null) {
            log.warn("dead letter message incomplete, skip: {}", message);
            return;
        }

        Long userId = message.getUserId();
        Long activityId = message.getActivityId();

        var orderOpt = seckillRepository.findOrderByUserAndActivity(userId, activityId);
        if (orderOpt.isEmpty() && StringUtils.hasText(message.getIdempotencyKey())) {
            orderOpt = seckillRepository.findOrderByIdempotencyKey(message.getIdempotencyKey());
        }

        if (orderOpt.isPresent()) {
            SeckillOrder order = orderOpt.get();
            String status = order.getStatus();
            if (SeckillOrderStatus.SUCCESS.name().equals(status)) {
                log.info("dead letter ignore: already SUCCESS, userId={}, activityId={}", userId, activityId);
                return;
            }
            if (SeckillOrderStatus.FAILED.name().equals(status)) {
                if (seckillRedisService.hasGrabbedUser(activityId, userId)) {
                    log.warn("dead letter compensate: FAILED but Redis still held, rollback userId={}, activityId={}",
                            userId, activityId);
                    seckillRedisService.rollbackGrab(activityId, userId);
                }
                return;
            }
            // PROCESSING 卡住：标失败并还库存
            if (SeckillOrderStatus.PROCESSING.name().equals(status)) {
                order.setStatus(SeckillOrderStatus.FAILED.name());
                order.setFailReason("dead-letter reconcile: stuck in PROCESSING");
                seckillRepository.saveOrder(order);
                if (seckillRedisService.hasGrabbedUser(activityId, userId)) {
                    seckillRedisService.rollbackGrab(activityId, userId);
                }
                log.warn("dead letter reconcile: PROCESSING → FAILED, userId={}, activityId={}",
                        userId, activityId);
                return;
            }
        }

        // 无 DB 记录：若 Lua 已占名额则回滚
        if (seckillRedisService.hasGrabbedUser(activityId, userId)) {
            seckillRedisService.rollbackGrab(activityId, userId);
            log.warn("dead letter reconcile: no order but Redis held, rolled back userId={}, activityId={}",
                    userId, activityId);
            return;
        }

        log.info("dead letter reconcile: nothing to compensate, userId={}, activityId={}", userId, activityId);
    }

    private SeckillActivity findEnabledActivity(Long id) {
        SeckillActivity activity = seckillRepository.findActivityById(id)
                .orElseThrow(SeckillException::activityNotFound);
        if (activity.getStatus() == null || activity.getStatus() != 1) {
            throw SeckillException.activityNotFound();
        }
        return activity;
    }

    private void validateActivityWindow(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw SeckillException.activityNotInWindow();
        }
    }

    private void validateCreateRequest(CreateSeckillActivityRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("活动名称不能为空");
        }
        if (req.getTemplateId() == null) {
            throw new IllegalArgumentException("templateId 不能为空");
        }
        if (req.getSeckillStock() == null || req.getSeckillStock() <= 0) {
            throw new IllegalArgumentException("seckillStock 必须大于 0");
        }
        if (!StringUtils.hasText(req.getStartTime()) || !StringUtils.hasText(req.getEndTime())) {
            throw new IllegalArgumentException("startTime 和 endTime 不能为空");
        }
        LocalDateTime start = parseDateTime(req.getStartTime());
        LocalDateTime end = parseDateTime(req.getEndTime());
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endTime 必须晚于 startTime");
        }
    }

    private SeckillActivityResponse toActivityResponse(SeckillActivity activity) {
        SeckillActivityResponse resp = new SeckillActivityResponse();
        resp.setId(activity.getId());
        resp.setName(activity.getName());
        resp.setTemplateId(activity.getTemplateId());
        resp.setSeckillStock(activity.getSeckillStock());
        resp.setRemainingStock(seckillRedisService.getRemainingStock(activity.getId()));
        resp.setStartTime(format(activity.getStartTime()));
        resp.setEndTime(format(activity.getEndTime()));
        resp.setStatus(activity.getStatus());
        resp.setActivityPhase(resolvePhase(activity));
        couponRepository.findTemplateById(activity.getTemplateId())
                .ifPresent(t -> resp.setTemplateName(t.getName()));
        return resp;
    }

    private SeckillGrabResponse toGrabResponse(Long activityId, SeckillOrder order) {
        SeckillGrabResponse response = new SeckillGrabResponse();
        response.setActivityId(activityId);
        response.setStatus(order.getStatus());
        if (SeckillOrderStatus.PROCESSING.name().equals(order.getStatus())) {
            response.setMessage("排队中，请稍后查询结果");
        } else if (SeckillOrderStatus.SUCCESS.name().equals(order.getStatus())) {
            response.setMessage("抢券成功");
        } else {
            response.setMessage(order.getFailReason());
        }
        return response;
    }

    private SeckillResultResponse toResultResponse(Long activityId, SeckillOrder order) {
        SeckillResultResponse response = new SeckillResultResponse();
        response.setActivityId(activityId);
        response.setStatus(order.getStatus());
        response.setUserCouponId(order.getUserCouponId());
        response.setFailReason(order.getFailReason());
        return response;
    }

    private String resolvePhase(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            return "UPCOMING";
        }
        if (now.isAfter(activity.getEndTime())) {
            return "ENDED";
        }
        return "ONGOING";
    }

    private LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value.trim(), DATETIME_FMT);
    }

    private String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }

    private String truncate(String message) {
        if (message == null) {
            return "发券失败";
        }
        return message.length() > 250 ? message.substring(0, 250) : message;
    }
}
