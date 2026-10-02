package com.zx.media.client.dto;

/**
 * 章目录条目。
 */
public record ChapterInfo(
        int chapterNo,
        String title,
        String fileId,
        int wordCount,
        String assetType
) {
}
