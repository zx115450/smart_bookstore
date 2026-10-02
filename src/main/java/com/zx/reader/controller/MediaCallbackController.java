package com.zx.reader.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.common.exception.BusinessException;
import com.zx.media.client.LiteMediaClientImpl;
import com.zx.media.client.LiteMediaProperties;
import com.zx.reader.dto.MediaCallbackRequest;
import com.zx.reader.service.MediaCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒资切章 / 转码 Webhook 入口（无 JWT，校验 {@code X-Internal-Token}）。
 * <p>
 * 同步 TOC 失败返回 503，便于 Worker 有限重试（at-least-once）。
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/media")
@RequiredArgsConstructor
public class MediaCallbackController {

    private final MediaCallbackService mediaCallbackService;
    private final LiteMediaProperties mediaProperties;

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Void>> callback(
            @RequestHeader(value = LiteMediaClientImpl.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestBody MediaCallbackRequest body
    ) {
        if (!assertInternalToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "invalid or missing internal token"));
        }
        try {
            mediaCallbackService.handle(body);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (BusinessException ex) {
            log.warn("media callback sync failed fileId={}: {}",
                    body == null ? null : body.fileId(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
        }
    }

    private boolean assertInternalToken(String token) {
        String expected = mediaProperties.getInternalToken();
        if (!StringUtils.hasText(expected)) {
            log.error("media callback rejected: bookstore.media.lite-vod.internal-token 未配置");
            return false;
        }
        return expected.equals(token);
    }
}
