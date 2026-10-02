package com.zx.media.client.dto;

import java.util.List;

/**
 * DOCUMENT 子章目录。
 */
public record ChaptersResult(
        String sourceFileId,
        List<ChapterInfo> chapters
) {
}
