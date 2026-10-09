package com.zx.media.client.dto;

/**
 * 申请 multipart 上传凭证（对齐媒资 {@code POST /internal/medias/upload-signature/multipart}）。
 */
public record MultipartUploadRequest(
        String filename,
        String contentType,
        long contentLength,
        long partSize,
        String assetType
) {
    public static MultipartUploadRequest document(String filename, String contentType,
                                                  long contentLength, long partSize) {
        return new MultipartUploadRequest(filename, contentType, contentLength, partSize, "DOCUMENT");
    }
}
