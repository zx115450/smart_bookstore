package com.zx.bookstore.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("book")
public class Book {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("category_id")
    private Long categoryId;

    @TableField("isbn")
    private String isbn;

    @TableField("title")
    private String title;

    @TableField("author")
    private String author;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("price")
    private BigDecimal price;

    @TableField("sale_stock")
    private Integer saleStock = 0;

    @TableField("borrow_stock")
    private Integer borrowStock = 0;

    @TableField("borrow_days")
    private Integer borrowDays = 30;

    @TableField("status")
    private Integer status = 1;

    @TableField("description")
    private String description;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
