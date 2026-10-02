package com.zx.reader.dto;

/**
 * 上报阅读进度。{@code chapterId} 与 {@code chapterNo} 二选一。
 */
public class UpdateProgressRequest {

    private Long chapterId;
    private Integer chapterNo;
    private Integer charOffset;

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getChapterNo() {
        return chapterNo;
    }

    public void setChapterNo(Integer chapterNo) {
        this.chapterNo = chapterNo;
    }

    public Integer getCharOffset() {
        return charOffset;
    }

    public void setCharOffset(Integer charOffset) {
        this.charOffset = charOffset;
    }
}
