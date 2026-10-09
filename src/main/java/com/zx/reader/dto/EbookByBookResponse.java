package com.zx.reader.dto;

/**
 * 实体书绑定的线上书摘要（详情页「读电子书」入口）。
 */
public record EbookByBookResponse(Long ebookId, Long bookId, String title) {
}
