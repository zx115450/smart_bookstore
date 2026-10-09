package com.zx.media.client.dto;

import java.util.List;

/** multipart 上传凭证（一次发齐各片 URL）。 */
public record MultipartUploadSignature(
        String fileId,
        String objectKey,
        String uploadId,
        long partSize,
        int partCount,
        List<PartUrl> parts,
        long expireAt
) {
}
