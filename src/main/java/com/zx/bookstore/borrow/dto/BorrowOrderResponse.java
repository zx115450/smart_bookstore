package com.zx.bookstore.borrow.dto;

public class BorrowOrderResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    private Long bookId;
    private String bookTitle;
    private String status;
    private String borrowAt;
    private String dueAt;
    private String returnAt;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBorrowAt() { return borrowAt; }
    public void setBorrowAt(String borrowAt) { this.borrowAt = borrowAt; }
    public String getDueAt() { return dueAt; }
    public void setDueAt(String dueAt) { this.dueAt = dueAt; }
    public String getReturnAt() { return returnAt; }
    public void setReturnAt(String returnAt) { this.returnAt = returnAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
