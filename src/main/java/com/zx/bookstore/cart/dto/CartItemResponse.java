package com.zx.bookstore.cart.dto;

import java.math.BigDecimal;

public class CartItemResponse {

    private Long id;
    private Long bookId;
    private String bookTitle;
    private String coverUrl;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private Integer saleStock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public Integer getSaleStock() { return saleStock; }
    public void setSaleStock(Integer saleStock) { this.saleStock = saleStock; }
}
