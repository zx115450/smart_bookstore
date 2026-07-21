package com.zx.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 借阅情况统计响应，适合前端折线图 / 堆叠柱状图 + 饼图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowStatisticsResponse {

    /** 每日借阅趋势（按状态拆分） */
    private List<DailyBorrow> dailyBorrows;

    /** 统计周期内累计借阅单数 */
    private long totalBorrowCount;

    /** 当前借阅中数量 */
    private long currentBorrowedCount;

    /** 当前逾期未还数量 */
    private long overdueCount;

    /** 按状态聚合的数量 */
    private List<StatusCount> byStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBorrow {
        /** 日期：yyyy-MM-dd */
        private String date;
        /** 当日申请借阅数 */
        private long appliedCount;
        /** 当日借阅中数 */
        private long borrowedCount;
        /** 当日归还数 */
        private long returnedCount;
        /** 当日逾期数 */
        private long overdueCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        /** 借阅状态 */
        private String status;
        /** 该状态数量 */
        private long count;
    }
}
