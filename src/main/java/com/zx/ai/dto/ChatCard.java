package com.zx.ai.dto;

/**
 * AI 聊天响应中的结构化卡片，供前端渲染图书/推荐 UI。
 * <p>
 * type=book：查书结果；type=recommend：推荐列表（F 阶段）。
 */
public class ChatCard {

    public static final String TYPE_BOOK = "book";
    public static final String TYPE_RECOMMEND = "recommend";

    private String type;
    private Long bookId;
    private String title;
    private String author;
    private String shelfLocation;
    private Integer borrowStock;
    private Integer saleStock;
    /** 推荐理由，仅 type=recommend 时使用 */
    private String recommendReason;

    public ChatCard() {
    }

    /** 查书结果卡片（含可售库存，便于前端展示）。 */
    public static ChatCard book(Long bookId, String title, String author,
                                String shelfLocation, Integer borrowStock, Integer saleStock) {
        ChatCard card = new ChatCard();
        card.setType(TYPE_BOOK);
        card.setBookId(bookId);
        card.setTitle(title);
        card.setAuthor(author);
        card.setShelfLocation(shelfLocation);
        card.setBorrowStock(borrowStock);
        card.setSaleStock(saleStock);
        return card;
    }

    /** 推荐结果卡片（含 recommendReason）。 */
    public static ChatCard recommend(Long bookId, String title, String author,
                                     String shelfLocation, Integer borrowStock, String reason) {
        ChatCard card = new ChatCard();
        card.setType(TYPE_RECOMMEND);
        card.setBookId(bookId);
        card.setTitle(title);
        card.setAuthor(author);
        card.setShelfLocation(shelfLocation);
        card.setBorrowStock(borrowStock);
        card.setRecommendReason(reason);
        return card;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getShelfLocation() {
        return shelfLocation;
    }

    public void setShelfLocation(String shelfLocation) {
        this.shelfLocation = shelfLocation;
    }

    public Integer getBorrowStock() {
        return borrowStock;
    }

    public void setBorrowStock(Integer borrowStock) {
        this.borrowStock = borrowStock;
    }

    public Integer getSaleStock() {
        return saleStock;
    }

    public void setSaleStock(Integer saleStock) {
        this.saleStock = saleStock;
    }

    public String getRecommendReason() {
        return recommendReason;
    }

    public void setRecommendReason(String recommendReason) {
        this.recommendReason = recommendReason;
    }
}
