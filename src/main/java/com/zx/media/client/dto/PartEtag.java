package com.zx.media.client.dto;

/** 分片完成信息（etag 应原样保留引号）。 */
public record PartEtag(int partNumber, String etag) {
}
