package com.zx.reader.dto;

/**
 * 目录项：禁止下发 chapterFileId / sourceFileId / 预签名 URL。
 */
public class ChapterTocItemResponse {

    private Integer chapterNo;
    private String title;
    private Integer wordCount;
    /** true = 需借阅/购买后可读 */
    private boolean locked;

    public Integer getChapterNo() {
        return chapterNo;
    }

    public void setChapterNo(Integer chapterNo) {
        this.chapterNo = chapterNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
