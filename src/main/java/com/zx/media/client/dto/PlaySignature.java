package com.zx.media.client.dto;

/**
 * 视频播放签名（B6 图书配套视频）。
 */
public record PlaySignature(
        String fileId,
        String playUrl,
        String signature,
        long expireAt
) {
}
