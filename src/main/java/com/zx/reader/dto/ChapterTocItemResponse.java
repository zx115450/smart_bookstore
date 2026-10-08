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
    /** false = 电子书未关联实体书，付费章无法按借阅或购买解锁 */
    private boolean bookBound;

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

    public boolean isBookBound() {
        return bookBound;
    }

    public void setBookBound(boolean bookBound) {
        this.bookBound = bookBound;
    }
}
