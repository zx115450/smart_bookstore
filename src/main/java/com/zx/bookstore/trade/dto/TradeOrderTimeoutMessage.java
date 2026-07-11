package com.zx.bookstore.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderTimeoutMessage {

    private Long orderId;
    private String orderNo;
}
