package com.zx.reader.dto;

/**
 * 图书配套视频列表项（不含长期 playUrl）。
 */
public class BookMediaRefResponse {

    private Long id;
    private Long bookId;
    private String fileId;
    private String title;
    private String mediaType;
    private Integer previewSeconds;
    private Integer sortOrder;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Integer getPreviewSeconds() { return previewSeconds; }
    public void setPreviewSeconds(Integer previewSeconds) { this.previewSeconds = previewSeconds; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
