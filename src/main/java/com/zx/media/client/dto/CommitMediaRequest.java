package com.zx.media.client.dto;

/**
 * 直传完成后的 commit 请求（生产走 {@code POST /internal/medias}）。
 */
public record CommitMediaRequest(
        String fileId,
        String filename,
        String assetType,
        String splitRule,
        Boolean progressive,
        Integer previewSeconds
) {
    public static CommitMediaRequest document(String fileId, String filename, String splitRule) {
        return new CommitMediaRequest(fileId, filename, "DOCUMENT", splitRule, null, null);
    }
}
