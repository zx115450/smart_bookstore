package com.zx.bookstore.seckill.dto;

import lombok.Data;

@Data
public class SeckillActivityResponse {

    private Long id;
    private String name;
    private Long templateId;
    private String templateName;
    /** 活动配置总量（DB） */
    private Integer seckillStock;
    /** 实时剩余（Redis，略滞后可接受） */
    private Integer remainingStock;
    private String startTime;
    private String endTime;
    private Integer status;
    /** UPCOMING / ONGOING / ENDED */
    private String activityPhase;
}
