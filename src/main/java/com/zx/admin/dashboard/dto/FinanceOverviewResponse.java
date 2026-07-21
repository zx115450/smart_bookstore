package com.zx.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 财务概览指标，用于管理端首页营收卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceOverviewResponse {

    /** 今日营业额 */
    private BigDecimal todayRevenue;

    /** 今日订单数 */
    private long todayOrderCount;
}
