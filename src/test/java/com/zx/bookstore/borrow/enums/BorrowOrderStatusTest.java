package com.zx.bookstore.borrow.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 借阅订单状态机的单元测试。
 * <p>
 * <b>为什么用 {@code @ParameterizedTest} + {@code @CsvSource}？</b>
 * 状态机逻辑是“输入 → 输出”的布尔判断，用参数化测试可以把合法 / 非法迁移矩阵
 * 写在一张表里，避免写大量重复方法，同时让测试意图一目了然。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link ParameterizedTest} / {@link CsvSource}：JUnit 5 参数化测试注解，
 *       每一行 CSV 会生成一条独立用例。</li>
 *   <li>被测对象 {@link BorrowOrderStatus} 是枚举，无外部依赖，不启动 Spring。</li>
 * </ul>
 */
class BorrowOrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "APPLIED, BORROWED, true",
            "APPLIED, CANCELLED, true",
            "APPLIED, RETURNED, false",
            "APPLIED, APPLIED, false",
            "BORROWED, RETURNED, true",
            "BORROWED, OVERDUE, true",
            "BORROWED, CANCELLED, false",
            "OVERDUE, RETURNED, true",
            "OVERDUE, OVERDUE, false",
            "RETURNED, CANCELLED, false",
            "CANCELLED, BORROWED, false"
    })
    void shouldRespectStateMachine(String from, String to, boolean expected) {
        BorrowOrderStatus source = BorrowOrderStatus.valueOf(from);
        BorrowOrderStatus target = BorrowOrderStatus.valueOf(to);
        assertEquals(expected, source.canTransitTo(target));
    }

    @Test
    void terminalStatesShouldNotTransitAnywhere() {
        for (BorrowOrderStatus target : BorrowOrderStatus.values()) {
            assertFalse(BorrowOrderStatus.RETURNED.canTransitTo(target));
            assertFalse(BorrowOrderStatus.CANCELLED.canTransitTo(target));
        }
    }
}
