package com.zx.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营业额统计响应，适合前端折线图 / 柱状图 + 饼图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatisticsResponse {

    /** 每日趋势 */
    private List<DailyRevenue> dailyRevenues;

    /** 统计周期内累计营业额 */
    private BigDecimal totalRevenue;

    /** 统计周期内累计订单数 */
    private long totalOrderCount;

    /** 按订单状态聚合的金额与数量 */
    private List<StatusAmount> byStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenue {
        /** 日期：yyyy-MM-dd */
        private String date;
        /** 当日营业额 */
        private BigDecimal revenue;
        /** 当日订单数 */
        private long orderCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusAmount {
        /** 订单状态 */
        private String status;
        /** 该状态订单金额 */
        private BigDecimal amount;
        /** 该状态订单数量 */
        private long count;
    }
}
