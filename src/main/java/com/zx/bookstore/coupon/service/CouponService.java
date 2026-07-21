package com.zx.bookstore.coupon.service;

import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.coupon.compute.CouponDiscountCalculatorFactory;
import com.zx.bookstore.coupon.dto.AvailableCouponResponse;
import com.zx.bookstore.coupon.dto.CreateCouponTemplateRequest;
import com.zx.bookstore.coupon.dto.CouponTemplateResponse;
import com.zx.bookstore.coupon.dto.UpdateCouponTemplateRequest;
import com.zx.bookstore.coupon.dto.UserCouponResponse;
import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.entity.UserCoupon;
import com.zx.bookstore.coupon.enums.CouponObtainWay;
import com.zx.bookstore.coupon.enums.CouponType;
import com.zx.bookstore.coupon.enums.UserCouponStatus;
import com.zx.bookstore.coupon.exception.CouponException;
import com.zx.bookstore.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    public static final String CHECKIN_7_TEMPLATE_NAME = "CHECKIN_7";

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CouponRepository couponRepository;
    private final CouponDiscountCalculatorFactory discountCalculatorFactory;

    @Transactional
    public UserCoupon issueCheckin7Coupon(Long userId) {
        CouponTemplate template = couponRepository.findTemplateByName(CHECKIN_7_TEMPLATE_NAME)
                .orElseThrow(CouponException::templateNotFound);
        return issueCoupon(userId, template, CouponObtainWay.CHECKIN_7);
    }

    /**
     * P4 秒杀异步发券，由 MQ Consumer 调用（与 P2 签到同步发券路径不同）。
     * obtain_way=SECKILL，发券规则与签到券相同，P3 购书时可通用核销。
     */
    @Transactional
    public UserCoupon issueSeckillCoupon(Long userId, Long templateId) {
        CouponTemplate template = couponRepository.findTemplateById(templateId)
                .orElseThrow(CouponException::templateNotFound);
        return issueCoupon(userId, template, CouponObtainWay.SECKILL);
    }

    public PageResult<CouponTemplateResponse> listTemplatesAdmin(Integer status, long page, long size) {
        List<CouponTemplate> templates = couponRepository.pageTemplates(status, page, size);
        long total = couponRepository.countTemplates(status);
        List<CouponTemplateResponse> records = templates.stream()
                .map(this::toTemplateResponse)
                .toList();
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    public CouponTemplateResponse getTemplateAdmin(Long id) {
        CouponTemplate template = couponRepository.findTemplateById(id)
                .orElseThrow(CouponException::templateNotFound);
        return toTemplateResponse(template);
    }

    @Transactional
    public CouponTemplateResponse createTemplate(CreateCouponTemplateRequest req) {
        validateCreateTemplateRequest(req);
        CouponTemplate template = new CouponTemplate();
        template.setName(req.getName().trim());
        template.setCouponType(resolveCouponType(req.getCouponType()).name());
        template.setThresholdAmount(req.getThresholdAmount() == null ? BigDecimal.ZERO : req.getThresholdAmount());
        template.setDiscountAmount(req.getDiscountAmount());
        template.setTotalCount(req.getTotalCount() == null ? 0 : req.getTotalCount());
        template.setValidDays(req.getValidDays() == null ? 7 : req.getValidDays());
        template.setStatus(1);
        couponRepository.saveTemplate(template);
        return toTemplateResponse(template);
    }

    @Transactional
    public CouponTemplateResponse updateTemplate(Long id, UpdateCouponTemplateRequest req) {
        CouponTemplate template = couponRepository.findTemplateById(id)
                .orElseThrow(CouponException::templateNotFound);
        if (req != null) {
            if (StringUtils.hasText(req.getName())) {
                String name = req.getName().trim();
                if (couponRepository.existsTemplateByNameExceptId(name, id)) {
                    throw new IllegalArgumentException("券模板名称已存在");
                }
                template.setName(name);
            }
            if (StringUtils.hasText(req.getCouponType())) {
                template.setCouponType(resolveCouponType(req.getCouponType()).name());
            }
            if (req.getThresholdAmount() != null) {
                template.setThresholdAmount(req.getThresholdAmount());
            }
            if (req.getDiscountAmount() != null) {
                template.setDiscountAmount(req.getDiscountAmount());
            }
            if (req.getTotalCount() != null) {
                if (req.getTotalCount() > 0 && template.getIssuedCount() != null
                        && req.getTotalCount() < template.getIssuedCount()) {
                    throw new IllegalArgumentException("发行总量不能小于已发放数量");
                }
                template.setTotalCount(req.getTotalCount());
            }
            if (req.getValidDays() != null) {
                template.setValidDays(req.getValidDays());
            }
            if (req.getStatus() != null) {
                template.setStatus(req.getStatus());
            }
        }
        couponRepository.saveTemplate(template);
        return toTemplateResponse(template);
    }

    @Transactional
    public void disableTemplate(Long id) {
        CouponTemplate template = couponRepository.findTemplateById(id)
                .orElseThrow(CouponException::templateNotFound);
        if (template.getStatus() != null && template.getStatus() == 0) {
            return;
        }
        couponRepository.updateTemplateStatus(id, 0);
    }

    /** 签到 / 秒杀共用发券逻辑，区别仅在 obtain_way 与调用时机（同步 vs MQ 异步）。 */
    private UserCoupon issueCoupon(Long userId, CouponTemplate template, CouponObtainWay obtainWay) {
        if (template.getTotalCount() != null && template.getTotalCount() > 0) {
            if (!couponRepository.incrementIssuedCount(template.getId())) {
                throw CouponException.exhausted();
            }
        }
        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setTemplateId(template.getId());
        coupon.setStatus(UserCouponStatus.UNUSED.name());
        coupon.setObtainWay(obtainWay.name());
        int validDays = template.getValidDays() == null ? 14 : template.getValidDays();
        coupon.setExpireAt(LocalDateTime.now().plusDays(validDays));
        return couponRepository.saveUserCoupon(coupon);
    }

    public List<UserCouponResponse> listMine(Long userId, String status) {
        return couponRepository.listByUser(userId, status).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AvailableCouponResponse> listAvailable(Long userId, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<AvailableCouponResponse> result = new ArrayList<>();
        for (UserCoupon coupon : couponRepository.listUnusedByUser(userId)) {
            CouponTemplate template = couponRepository.findTemplateById(coupon.getTemplateId())
                    .orElse(null);
            if (template == null) {
                continue;
            }
            BigDecimal threshold = template.getThresholdAmount() == null ? BigDecimal.ZERO : template.getThresholdAmount();
            if (orderAmount.compareTo(threshold) < 0) {
                continue;
            }
            BigDecimal estimatedDiscount = discountCalculatorFactory
                    .getCalculator(template.getCouponType())
                    .calculate(template, orderAmount);
            AvailableCouponResponse resp = new AvailableCouponResponse();
            resp.setId(coupon.getId());
            resp.setTemplateId(template.getId());
            resp.setTemplateName(displayTemplateName(template.getName()));
            resp.setCouponType(template.getCouponType());
            resp.setThresholdAmount(template.getThresholdAmount());
            resp.setDiscountAmount(template.getDiscountAmount());
            resp.setExpireAt(format(coupon.getExpireAt()));
            resp.setEstimatedDiscount(estimatedDiscount);
            result.add(resp);
        }
        return result;
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
        return discountCalculatorFactory
                .getCalculator(template.getCouponType())
                .calculate(template, totalAmount);
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

    private CouponTemplateResponse toTemplateResponse(CouponTemplate template) {
        CouponTemplateResponse resp = new CouponTemplateResponse();
        resp.setId(template.getId());
        resp.setName(template.getName());
        resp.setCouponType(template.getCouponType());
        resp.setThresholdAmount(template.getThresholdAmount());
        resp.setDiscountAmount(template.getDiscountAmount());
        resp.setTotalCount(template.getTotalCount());
        resp.setIssuedCount(template.getIssuedCount());
        resp.setValidDays(template.getValidDays());
        resp.setStatus(template.getStatus());
        return resp;
    }

    private void validateCreateTemplateRequest(CreateCouponTemplateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (couponRepository.existsTemplateByNameExceptId(req.getName().trim(), null)) {
            throw new IllegalArgumentException("券模板名称已存在");
        }
        resolveCouponType(req.getCouponType());
        if (req.getDiscountAmount() == null) {
            throw new IllegalArgumentException("discountAmount 不能为空");
        }
    }

    private CouponType resolveCouponType(String couponType) {
        if (!StringUtils.hasText(couponType)) {
            return CouponType.FIXED;
        }
        try {
            return CouponType.valueOf(couponType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw CouponException.unsupportedCouponType();
        }
    }
}
