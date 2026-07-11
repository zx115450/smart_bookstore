package com.zx.bookstore.seckill.dto;

import lombok.Data;

@Data
public class SeckillResultResponse {

    private Long activityId;
    private String status;
    private Long userCouponId;
    private String failReason;
}
