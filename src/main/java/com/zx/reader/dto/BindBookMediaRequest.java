package com.zx.reader.dto;

/**
 * 管理端绑定图书配套视频。
 */
public class BindBookMediaRequest {

    private String fileId;
    private String title;
    /** INTRO / LECTURE / TRAILER；缺省 INTRO。 */
    private String mediaType;
    private Integer previewSeconds;
    private Integer sortOrder;

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
}
