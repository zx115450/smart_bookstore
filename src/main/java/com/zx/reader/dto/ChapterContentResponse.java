package com.zx.reader.dto;

/**
 * 读章正文响应（BFF 已鉴权并拉媒资文本）。
 */
public class ChapterContentResponse {

    private Integer chapterNo;
    private String title;
    private String content;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
