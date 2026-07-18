package com.zx.bookstore.catalog.dto;

import java.math.BigDecimal;

public class UpdateBookRequest {
    private Long categoryId;
    private String isbn;
    private String title;
    private String author;
    private String coverUrl;
    private BigDecimal price;
    private Integer saleStock;
    private Integer borrowStock;
    private Integer borrowDays;
    private Integer status;
    private String description;
    private Long bookshelfId;
    private Integer shelfLayer;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getSaleStock() { return saleStock; }
    public void setSaleStock(Integer saleStock) { this.saleStock = saleStock; }
    public Integer getBorrowStock() { return borrowStock; }
    public void setBorrowStock(Integer borrowStock) { this.borrowStock = borrowStock; }
    public Integer getBorrowDays() { return borrowDays; }
    public void setBorrowDays(Integer borrowDays) { this.borrowDays = borrowDays; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getBookshelfId() { return bookshelfId; }
    public void setBookshelfId(Long bookshelfId) { this.bookshelfId = bookshelfId; }
    public Integer getShelfLayer() { return shelfLayer; }
    public void setShelfLayer(Integer shelfLayer) { this.shelfLayer = shelfLayer; }
}
