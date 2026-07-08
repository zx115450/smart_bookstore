package com.zx.bookstore.trade.dto;

import java.math.BigDecimal;
import java.util.List;

public class TradeOrderResponse {

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Long couponId;
    private String status;
    private String paidAt;
    private List<TradeOrderItemResponse> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }
    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
    public List<TradeOrderItemResponse> getItems() { return items; }
    public void setItems(List<TradeOrderItemResponse> items) { this.items = items; }

    public static class TradeOrderItemResponse {
        private Long bookId;
        private String bookTitle;
        private BigDecimal price;
        private Integer quantity;

        public Long getBookId() { return bookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public String getBookTitle() { return bookTitle; }
        public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
