package com.zx.reader.dto;

/**
 * 管理端手动同步 TOC 结果。
 */
public class SyncChaptersResponse {

    private Long ebookId;
    private String sourceFileId;
    private int chapterCount;

    public SyncChaptersResponse() {
    }

    public SyncChaptersResponse(Long ebookId, String sourceFileId, int chapterCount) {
        this.ebookId = ebookId;
        this.sourceFileId = sourceFileId;
        this.chapterCount = chapterCount;
    }

    public Long getEbookId() {
        return ebookId;
    }

    public void setEbookId(Long ebookId) {
        this.ebookId = ebookId;
    }

    public String getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(String sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public int getChapterCount() {
        return chapterCount;
    }

    public void setChapterCount(int chapterCount) {
        this.chapterCount = chapterCount;
    }
}
