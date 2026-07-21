package com.zx.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存与用户概览指标，用于管理端首页资源卡片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOverviewResponse {

    /** 上架图书总数 */
    private long totalBookCount;

    /** 注册用户总数 */
    private long totalUserCount;
}
