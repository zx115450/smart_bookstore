package com.zx.admin.dashboard.controller;

import com.zx.admin.dashboard.dto.BorrowOverviewResponse;
import com.zx.admin.dashboard.dto.BorrowStatisticsResponse;
import com.zx.admin.dashboard.dto.FinanceOverviewResponse;
import com.zx.admin.dashboard.dto.InventoryOverviewResponse;
import com.zx.admin.dashboard.dto.RevenueStatisticsResponse;
import com.zx.admin.dashboard.service.DashboardService;
import com.zx.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据看板接口，为前端统计图表提供数据。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardService dashboardService;

    /**
     * 财务概览：今日营业额、今日订单数。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview/finance")
    public ApiResponse<FinanceOverviewResponse> financeOverview() {
        return ApiResponse.ok(dashboardService.financeOverview());
    }

    /**
     * 借阅概览：今日借阅数、待处理借阅、逾期未还。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview/borrow")
    public ApiResponse<BorrowOverviewResponse> borrowOverview() {
        return ApiResponse.ok(dashboardService.borrowOverview());
    }

    /**
     * 库存与用户概览：图书总数、用户总数。
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/overview/inventory")
    public ApiResponse<InventoryOverviewResponse> inventoryOverview() {
        return ApiResponse.ok(dashboardService.inventoryOverview());
    }

    /**
     * 营业额统计：每日趋势 + 按状态分布。
     *
     * @param days 最近 N 天，默认 30，范围 7~90
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/revenue")
    public ApiResponse<RevenueStatisticsResponse> revenue(
            @RequestParam(required = false, defaultValue = "30") int days
    ) {
        return ApiResponse.ok(dashboardService.revenueStatistics(days));
    }

    /**
     * 借阅情况统计：每日趋势 + 状态分布。
     *
     * @param days 最近 N 天，默认 30，范围 7~90
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/borrow")
    public ApiResponse<BorrowStatisticsResponse> borrow(
            @RequestParam(required = false, defaultValue = "30") int days
    ) {
        return ApiResponse.ok(dashboardService.borrowStatistics(days));
    }
}
