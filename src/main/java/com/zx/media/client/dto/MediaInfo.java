package com.zx.media.client.dto;

/**
 * 媒资详情（对齐 vod {@code MediaDto} 消费侧字段）。
 */
public record MediaInfo(
        String fileId,
        String assetType,
        String filename,
        String mimeType,
        String parentFileId,
        Integer chapterNo,
        String status,
        String statusText,
        String errorMsg
) {
}
