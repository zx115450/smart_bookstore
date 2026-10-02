package com.zx.reader.service;

import com.zx.reader.dto.MediaCallbackRequest;
import com.zx.reader.repository.EbookBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCallbackServiceTest {

    @Mock
    private EbookTocSyncService ebookTocSyncService;
    @Mock
    private EbookBookRepository ebookBookRepository;

    @InjectMocks
    private MediaCallbackService service;

    @Test
    void shouldIgnoreProcedureEvents() {
        service.handle(new MediaCallbackRequest(
                "video-1", MediaCallbackRequest.STATUS_PROCESSED, null, 1.0, null,
                MediaCallbackRequest.EVENT_PROCEDURE));
        verify(ebookTocSyncService, never()).syncBySourceFileId(anyString());
    }

    @Test
    void shouldSyncOnDocumentProcessed() {
        when(ebookTocSyncService.syncBySourceFileId("doc-1")).thenReturn(3);
        service.handle(new MediaCallbackRequest(
                "doc-1", MediaCallbackRequest.STATUS_PROCESSED, null, null, null,
                MediaCallbackRequest.EVENT_DOCUMENT_SPLIT));
        verify(ebookTocSyncService).syncBySourceFileId("doc-1");
    }

    @Test
    void shouldNotSyncOnFailed() {
        when(ebookBookRepository.findBySourceFileId("doc-1")).thenReturn(Optional.empty());
        service.handle(new MediaCallbackRequest(
                "doc-1", MediaCallbackRequest.STATUS_FAILED, null, null, "no chapters",
                MediaCallbackRequest.EVENT_DOCUMENT_SPLIT));
        verify(ebookTocSyncService, never()).syncBySourceFileId(anyString());
    }
}
