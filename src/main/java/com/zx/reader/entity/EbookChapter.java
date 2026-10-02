package com.zx.reader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 章节目录。正文不落库，经 {@code chapter_file_id} 向媒资拉文本。
 */
@TableName("ebook_chapter")
public class EbookChapter {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("ebook_id")
    private Long ebookId;

    @TableField("chapter_no")
    private Integer chapterNo;

    @TableField("title")
    private String title;

    /** 媒资 CHAPTER fileId，不跨库 FK。 */
    @TableField("chapter_file_id")
    private String chapterFileId;

    @TableField("word_count")
    private Integer wordCount = 0;

    @TableField("is_preview_free")
    private Integer isPreviewFree = 0;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
    public Integer getChapterNo() { return chapterNo; }
    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getChapterFileId() { return chapterFileId; }
    public void setChapterFileId(String chapterFileId) { this.chapterFileId = chapterFileId; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public Integer getIsPreviewFree() { return isPreviewFree; }
    public void setIsPreviewFree(Integer isPreviewFree) { this.isPreviewFree = isPreviewFree; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
