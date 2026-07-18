package com.zx.bookstore.borrow.dto;

public class BorrowOrderResponse {

    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    private Long bookId;
    private String bookTitle;
    private Integer bookshelfFloor;
    private String bookshelfCode;
    private Integer shelfLayer;
    private String shelfLocation;
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
    public Integer getBookshelfFloor() { return bookshelfFloor; }
    public void setBookshelfFloor(Integer bookshelfFloor) { this.bookshelfFloor = bookshelfFloor; }
    public String getBookshelfCode() { return bookshelfCode; }
    public void setBookshelfCode(String bookshelfCode) { this.bookshelfCode = bookshelfCode; }
    public Integer getShelfLayer() { return shelfLayer; }
    public void setShelfLayer(Integer shelfLayer) { this.shelfLayer = shelfLayer; }
    public String getShelfLocation() { return shelfLocation; }
    public void setShelfLocation(String shelfLocation) { this.shelfLocation = shelfLocation; }
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
