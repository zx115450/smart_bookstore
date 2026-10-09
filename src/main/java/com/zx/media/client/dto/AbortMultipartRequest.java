package com.zx.media.client.dto;

/** 中止分片上传。 */
public record AbortMultipartRequest(String uploadId) {
}
