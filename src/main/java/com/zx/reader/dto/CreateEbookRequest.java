package com.zx.reader.dto;

/**
 * 管理端创建线上书。
 */
public class CreateEbookRequest {

    private String title;
    private String author;
    /** 可选：绑定实体书。 */
    private Long bookId;
    /** MARKDOWN / TXT；缺省 MARKDOWN。 */
    private String format;
    /** 试看免费章数；缺省用 reader.preview.default-chapters。 */
    private Integer previewChapters;
    private String coverUrl;

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

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getPreviewChapters() {
        return previewChapters;
    }

    public void setPreviewChapters(Integer previewChapters) {
        this.previewChapters = previewChapters;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
}
