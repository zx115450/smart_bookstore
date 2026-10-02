package com.zx.media.client.dto;

/**
 * 上传预签名（浏览器直传 MinIO）。
 */
public record UploadSignature(
        String fileId,
        String uploadUrl,
        String objectKey,
        long expireAt
) {
}
