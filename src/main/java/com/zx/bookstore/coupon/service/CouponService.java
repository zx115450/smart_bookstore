package com.zx.bookstore.coupon.service;

import com.zx.bookstore.coupon.dto.UserCouponResponse;
import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.entity.UserCoupon;
import com.zx.bookstore.coupon.enums.CouponObtainWay;
import com.zx.bookstore.coupon.enums.UserCouponStatus;
import com.zx.bookstore.coupon.exception.CouponException;
import com.zx.bookstore.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    public static final String CHECKIN_7_TEMPLATE_NAME = "CHECKIN_7";

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CouponRepository couponRepository;

    @Transactional
    public UserCoupon issueCheckin7Coupon(Long userId) {
        CouponTemplate template = couponRepository.findTemplateByName(CHECKIN_7_TEMPLATE_NAME)
                .orElseThrow(CouponException::templateNotFound);
        if (template.getTotalCount() != null && template.getTotalCount() > 0) {
            if (!couponRepository.incrementIssuedCount(template.getId())) {
                throw new CouponException(4105, "签到赠券已发完");
            }
        }
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setTemplateId(template.getId());
        coupon.setStatus(UserCouponStatus.UNUSED.name());
        coupon.setObtainWay(CouponObtainWay.CHECKIN_7.name());
        int validDays = template.getValidDays() == null ? 14 : template.getValidDays();
        coupon.setExpireAt(LocalDateTime.now().plusDays(validDays));
        return couponRepository.saveUserCoupon(coupon);
    }

    public List<UserCouponResponse> listMine(Long userId, String status) {
        return couponRepository.listByUser(userId, status).stream()
                .map(this::toResponse)
                .toList();
    }

    public BigDecimal calculateDiscount(Long userId, Long userCouponId, BigDecimal totalAmount) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        UserCoupon coupon = couponRepository.findUserCouponById(userCouponId)
                .orElseThrow(CouponException::notFound);
        if (!coupon.getUserId().equals(userId)) {
            throw CouponException.notFound();
        }
        if (!UserCouponStatus.UNUSED.name().equals(coupon.getStatus())) {
            throw CouponException.notUsable();
        }
        if (coupon.getExpireAt() != null && coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            throw CouponException.notUsable();
        }
        CouponTemplate template = couponRepository.findTemplateById(coupon.getTemplateId())
                .orElseThrow(CouponException::templateNotFound);
        BigDecimal threshold = template.getThresholdAmount() == null ? BigDecimal.ZERO : template.getThresholdAmount();
        if (totalAmount.compareTo(threshold) < 0) {
            throw CouponException.thresholdNotMet();
        }
        if ("PERCENT".equals(template.getCouponType())) {
            BigDecimal rate = template.getDiscountAmount().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return totalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = template.getDiscountAmount() == null ? BigDecimal.ZERO : template.getDiscountAmount();
        return discount.min(totalAmount);
    }

    @Transactional
    public void markUsed(Long userId, Long userCouponId, Long tradeOrderId) {
        if (userCouponId == null) {
            return;
        }
        if (!couponRepository.markUsed(userCouponId, userId, tradeOrderId)) {
            throw CouponException.notUsable();
        }
    }

    private UserCouponResponse toResponse(UserCoupon coupon) {
        UserCouponResponse resp = new UserCouponResponse();
        resp.setId(coupon.getId());
        resp.setTemplateId(coupon.getTemplateId());
        resp.setStatus(coupon.getStatus());
        resp.setObtainWay(coupon.getObtainWay());
        resp.setExpireAt(format(coupon.getExpireAt()));
        resp.setUsedAt(format(coupon.getUsedAt()));
        couponRepository.findTemplateById(coupon.getTemplateId()).ifPresent(t -> {
            resp.setTemplateName(displayTemplateName(t.getName()));
            resp.setCouponType(t.getCouponType());
            resp.setThresholdAmount(t.getThresholdAmount());
            resp.setDiscountAmount(t.getDiscountAmount());
        });
        return resp;
    }

    private String displayTemplateName(String name) {
        if ("CHECKIN_7".equals(name)) {
            return "连续签到7天赠券";
        }
        return name;
    }

    private String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }
}
