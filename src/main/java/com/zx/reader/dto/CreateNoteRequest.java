package com.zx.reader.dto;

public class CreateNoteRequest {

    private Long ebookId;
    private Long chapterId;
    private Long bookId;
    private String fileId;
    /** MANUAL / HIGHLIGHT；缺省 MANUAL。有 quoteText 时默认 HIGHLIGHT。 */
    private String sourceType;
    private String title;
    private String content;
    private String quoteText;
    private String tags;

    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}
