package com.zx.media.client.dto;

/**
 * 内部 object-url 响应（书城再 GET {@code objectUrl} 取正文）。
 */
public record ObjectUrlResponse(
        String fileId,
        String objectUrl,
        String objectKey,
        String assetType,
        long expireAt
) {
}
