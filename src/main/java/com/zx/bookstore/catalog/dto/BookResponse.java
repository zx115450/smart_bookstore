package com.zx.bookstore.catalog.dto;

import java.math.BigDecimal;

public class BookResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
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
    private Integer bookshelfFloor;
    private String bookshelfCode;
    private Integer shelfLayer;
    private String shelfLocation;
    /** 绑定的上架线上书 ID；无电子书时为 null。 */
    private Long ebookId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
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
    public Integer getBookshelfFloor() { return bookshelfFloor; }
    public void setBookshelfFloor(Integer bookshelfFloor) { this.bookshelfFloor = bookshelfFloor; }
    public String getBookshelfCode() { return bookshelfCode; }
    public void setBookshelfCode(String bookshelfCode) { this.bookshelfCode = bookshelfCode; }
    public Integer getShelfLayer() { return shelfLayer; }
    public void setShelfLayer(Integer shelfLayer) { this.shelfLayer = shelfLayer; }
    public String getShelfLocation() { return shelfLocation; }
    public void setShelfLocation(String shelfLocation) { this.shelfLocation = shelfLocation; }
    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
}
