package com.zx.bookstore.coupon.compute;

import com.zx.bookstore.coupon.entity.CouponTemplate;
import com.zx.bookstore.coupon.enums.CouponType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 优惠券计价策略的单元测试：覆盖满减、百分比、未达门槛、空值等分支。
 * <p>
 * <b>为什么用纯 JUnit 5？</b>
 * 计价器只依赖 {@link CouponTemplate} 和 {@link BigDecimal}，没有 Spring 容器或 Repository，
 * 适合写成最轻量的纯逻辑测试，作为回归保护网。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link CouponTemplate}：被测方法入参，只用 setter 填充折扣金额与类型。</li>
 *   <li>{@link CouponType}：枚举，决定走 {@link FixedAmountCalculator} 还是 {@link PercentDiscountCalculator}。</li>
 *   <li>测试里直接实例化两个具体 Calculator，没有使用 Spring 注入，保持最小依赖。</li>
 * </ul>
 */
class CouponDiscountCalculatorTest {

    private final FixedAmountCalculator fixed = new FixedAmountCalculator();
    private final PercentDiscountCalculator percent = new PercentDiscountCalculator();

    @Test
    void fixedCalculator_shouldSupportFixedType() {
        assertTrue(fixed.supports("FIXED"));
        assertFalse(fixed.supports("PERCENT"));
    }

    @Test
    void fixedCalculator_shouldReturnCappedDiscount() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponType(CouponType.FIXED.name());
        template.setDiscountAmount(new BigDecimal("50"));

        BigDecimal total = new BigDecimal("100");
        assertEquals(new BigDecimal("50"), fixed.calculate(template, total));

        BigDecimal smallTotal = new BigDecimal("30");
        assertEquals(new BigDecimal("30"), fixed.calculate(template, smallTotal));
    }

    @Test
    void fixedCalculator_shouldHandleNullDiscount() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponType(CouponType.FIXED.name());
        template.setDiscountAmount(null);

        assertEquals(BigDecimal.ZERO, fixed.calculate(template, new BigDecimal("100")));
    }

    @Test
    void percentCalculator_shouldSupportPercentType() {
        assertTrue(percent.supports("PERCENT"));
        assertFalse(percent.supports("FIXED"));
    }

    @Test
    void percentCalculator_shouldReturnPercentOfTotal() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponType(CouponType.PERCENT.name());
        template.setDiscountAmount(new BigDecimal("20"));

        assertEquals(new BigDecimal("20.00"), percent.calculate(template, new BigDecimal("100")));
        assertEquals(new BigDecimal("15.00"), percent.calculate(template, new BigDecimal("75")));
    }

    @Test
    void percentCalculator_shouldHandleNullDiscount() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponType(CouponType.PERCENT.name());
        template.setDiscountAmount(null);

        assertEquals(BigDecimal.ZERO.setScale(2), percent.calculate(template, new BigDecimal("100")));
    }
}
