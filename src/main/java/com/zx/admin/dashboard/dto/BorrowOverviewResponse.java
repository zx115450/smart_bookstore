package com.zx.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 借阅概览指标，用于管理端首页借阅卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowOverviewResponse {

    /** 今日借阅单数 */
    private long todayBorrowCount;

    /** 待处理借阅申请数 */
    private long pendingBorrowCount;

    /** 逾期未还数量 */
    private long overdueBorrowCount;
}
