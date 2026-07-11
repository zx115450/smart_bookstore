package com.zx.bookstore.seckill.dto;

import lombok.Data;

@Data
public class CreateSeckillActivityRequest {

    private String name;
    private Long templateId;
    private Integer seckillStock;
    private String startTime;
    private String endTime;
}
