package com.zx.reader.dto;

public class UpdateNoteRequest {

    private String title;
    private String content;
    private String quoteText;
    private String tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}
