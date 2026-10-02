package com.zx.reader.dto;

/**
 * 媒资 Webhook 回调体（与 vod-worker {@code CallbackPayload} 对齐）。
 */
public record MediaCallbackRequest(
        String fileId,
        String status,
        String coverUrl,
        Double duration,
        String errorMsg,
        String eventType
) {
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String EVENT_PROCEDURE = "PROCEDURE";
    public static final String EVENT_DOCUMENT_SPLIT = "DOCUMENT_SPLIT";
}
