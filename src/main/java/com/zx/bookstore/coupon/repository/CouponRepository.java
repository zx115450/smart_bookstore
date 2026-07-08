package com.zx.bookstore.coupon.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.entity.UserCoupon;
import com.zx.bookstore.coupon.enums.UserCouponStatus;
import com.zx.bookstore.coupon.mapper.CouponTemplateMapper;
import com.zx.bookstore.coupon.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponRepository {

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;

    public Optional<CouponTemplate> findTemplateByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(templateMapper.selectOne(
                Wrappers.<CouponTemplate>lambdaQuery()
                        .eq(CouponTemplate::getName, name)
                        .eq(CouponTemplate::getStatus, 1)
        ));
    }

    public Optional<CouponTemplate> findTemplateById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templateMapper.selectById(id));
    }

    public boolean incrementIssuedCount(Long templateId) {
        return templateMapper.incrementIssuedCount(templateId) > 0;
    }

    public Optional<UserCoupon> findUserCouponById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userCouponMapper.selectById(id));
    }

    public Optional<UserCoupon> findUserCouponByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userCouponMapper.selectByIdForUpdate(id));
    }

    public List<UserCoupon> listByUser(Long userId, String status) {
        var wrapper = Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        return userCouponMapper.selectList(wrapper);
    }

    public UserCoupon saveUserCoupon(UserCoupon coupon) {
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getId() == null) {
            if (coupon.getCreatedAt() == null) {
                coupon.setCreatedAt(now);
            }
            coupon.setUpdatedAt(now);
            userCouponMapper.insert(coupon);
            return coupon;
        }
        coupon.setUpdatedAt(now);
        userCouponMapper.updateById(coupon);
        return coupon;
    }

    public boolean markUsed(Long couponId, Long userId, Long tradeOrderId) {
        return userCouponMapper.markUsed(couponId, userId, tradeOrderId) > 0;
    }

    public List<UserCoupon> listUnusedByUser(Long userId) {
        return userCouponMapper.selectList(
                Wrappers.<UserCoupon>lambdaQuery()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, UserCouponStatus.UNUSED.name())
                        .gt(UserCoupon::getExpireAt, LocalDateTime.now())
                        .orderByAsc(UserCoupon::getExpireAt)
        );
    }
}
