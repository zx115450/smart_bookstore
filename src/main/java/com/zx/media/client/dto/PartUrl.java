package com.zx.media.client.dto;

/** 单个分片预签名上传 URL。 */
public record PartUrl(int partNumber, String uploadUrl) {
}
