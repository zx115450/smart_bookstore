package com.zx.bookstore.trade.dto;

import java.math.BigDecimal;
import java.util.List;

public class CreateTradeOrderRequest {

    private Long bookId;
    private Integer quantity;
    private Long userCouponId;
    private String idempotencyKey;

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
