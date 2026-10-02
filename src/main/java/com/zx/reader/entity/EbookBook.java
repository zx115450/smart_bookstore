package com.zx.reader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ebook_book")
public class EbookBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("book_id")
    private Long bookId;

    @TableField("title")
    private String title;

    @TableField("author")
    private String author;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("format")
    private String format = "MARKDOWN";

    @TableField("status")
    private Integer status = 1;

    @TableField("preview_mode")
    private String previewMode = "CHAPTER";

    @TableField("preview_chapters")
    private Integer previewChapters = 2;

    @TableField("total_chapters")
    private Integer totalChapters = 0;

    @TableField("word_count")
    private Long wordCount = 0L;

    /** 媒资 DOCUMENT fileId，不跨库 FK。 */
    @TableField("source_file_id")
    private String sourceFileId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPreviewMode() { return previewMode; }
    public void setPreviewMode(String previewMode) { this.previewMode = previewMode; }
    public Integer getPreviewChapters() { return previewChapters; }
    public void setPreviewChapters(Integer previewChapters) { this.previewChapters = previewChapters; }
    public Integer getTotalChapters() { return totalChapters; }
    public void setTotalChapters(Integer totalChapters) { this.totalChapters = totalChapters; }
    public Long getWordCount() { return wordCount; }
    public void setWordCount(Long wordCount) { this.wordCount = wordCount; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
