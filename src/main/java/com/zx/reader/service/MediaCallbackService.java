package com.zx.reader.service;

import com.zx.reader.dto.MediaCallbackRequest;
import com.zx.reader.repository.EbookBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 处理媒资 Webhook：切章成功则同步 TOC；视频 PROCEDURE 忽略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCallbackService {

    private final EbookTocSyncService ebookTocSyncService;
    private final EbookBookRepository ebookBookRepository;

    public void handle(MediaCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.fileId())) {
            log.warn("media callback ignored: empty payload");
            return;
        }

        String eventType = request.eventType();
        if (MediaCallbackRequest.EVENT_PROCEDURE.equals(eventType)) {
            log.debug("media callback skipped (PROCEDURE): fileId={}", request.fileId());
            return;
        }

        String status = request.status();
        if (MediaCallbackRequest.STATUS_FAILED.equals(status)) {
            if (ebookBookRepository.findBySourceFileId(request.fileId()).isPresent()) {
                log.warn("document split FAILED for ebook sourceFileId={} error={}",
                        request.fileId(), request.errorMsg());
            } else {
                log.info("media callback FAILED (no ebook bound): fileId={} error={}",
                        request.fileId(), request.errorMsg());
            }
            return;
        }

        if (!MediaCallbackRequest.STATUS_PROCESSED.equals(status)) {
            log.info("media callback ignored unknown status={} fileId={}", status, request.fileId());
            return;
        }

        // DOCUMENT_SPLIT 或未标 eventType（兼容旧 Worker）：按 source_file_id 尝试同步
        if (eventType != null
                && !eventType.isBlank()
                && !MediaCallbackRequest.EVENT_DOCUMENT_SPLIT.equals(eventType)) {
            log.debug("media callback skipped eventType={} fileId={}", eventType, request.fileId());
            return;
        }

        int synced = ebookTocSyncService.syncBySourceFileId(request.fileId());
        if (synced < 0) {
            log.info("media callback PROCESSED but no ebook for sourceFileId={}", request.fileId());
        } else {
            log.info("media callback TOC synced sourceFileId={} chapters={}", request.fileId(), synced);
        }
    }
}
