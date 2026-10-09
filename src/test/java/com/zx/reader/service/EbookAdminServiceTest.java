package com.zx.reader.service;

import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.common.exception.BusinessException;
import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.LiteMediaProperties;
import com.zx.media.client.MockLiteMediaClient;
import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
import com.zx.media.client.dto.PartEtag;
import com.zx.media.client.dto.PartUrl;
import com.zx.media.client.dto.UploadSignature;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.AbortMultipartUploadRequest;
import com.zx.reader.dto.CommitEbookRequest;
import com.zx.reader.dto.CompleteMultipartUploadRequest;
import com.zx.reader.dto.CreateEbookRequest;
import com.zx.reader.dto.EbookAdminResponse;
import com.zx.reader.dto.MultipartUploadSignatureRequest;
import com.zx.reader.dto.MultipartUploadSignatureResponse;
import com.zx.reader.dto.SyncChaptersResponse;
import com.zx.reader.dto.UploadSignatureResponse;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.repository.EbookBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbookAdminServiceTest {

    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private LiteMediaClient liteMediaClient;
    @Mock
    private EbookTocSyncService ebookTocSyncService;

    private LiteMediaProperties liteMediaProperties;
    private ReaderProperties readerProperties;
    private EbookAdminService service;

    @BeforeEach
    void setUp() {
        liteMediaProperties = new LiteMediaProperties();
        liteMediaProperties.setMock(true);
        readerProperties = new ReaderProperties();
        readerProperties.getPreview().setDefaultChapters(2);
        service = new EbookAdminService(
                ebookBookRepository,
                bookRepository,
                liteMediaClient,
                liteMediaProperties,
                ebookTocSyncService,
                readerProperties);
    }

    @Test
    void create_shouldPersistWithDefaults() {
        when(ebookBookRepository.save(any())).thenAnswer(inv -> {
            EbookBook b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });

        CreateEbookRequest req = new CreateEbookRequest();
        req.setTitle(" Redis 入门 ");
        req.setAuthor("张三");

        EbookAdminResponse resp = service.create(req);
        assertThat(resp.getId()).isEqualTo(99L);
        assertThat(resp.getTitle()).isEqualTo("Redis 入门");
        assertThat(resp.getFormat()).isEqualTo("MARKDOWN");
        assertThat(resp.getPreviewChapters()).isEqualTo(2);
        assertThat(resp.getBookId()).isNull();
    }

    @Test
    void create_shouldRejectMissingBook() {
        when(bookRepository.findById(8L)).thenReturn(Optional.empty());
        CreateEbookRequest req = new CreateEbookRequest();
        req.setTitle("x");
        req.setBookId(8L);
        assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
        verify(ebookBookRepository, never()).save(any());
    }

    @Test
    void create_shouldBindExistingBook() {
        Book catalog = new Book();
        catalog.setId(8L);
        when(bookRepository.findById(8L)).thenReturn(Optional.of(catalog));
        when(ebookBookRepository.save(any())).thenAnswer(inv -> {
            EbookBook b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        CreateEbookRequest req = new CreateEbookRequest();
        req.setTitle("绑定书");
        req.setBookId(8L);
        assertThat(service.create(req).getBookId()).isEqualTo(8L);
    }

    @Test
    void uploadSignature_shouldReturnClientResult() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(liteMediaClient.createUploadSignature("DOCUMENT"))
                .thenReturn(new UploadSignature("f1", "http://put", "raw/f1", 123L));

        UploadSignatureResponse resp = service.uploadSignature(5L);
        assertThat(resp.getEbookId()).isEqualTo(5L);
        assertThat(resp.getFileId()).isEqualTo("f1");
        assertThat(resp.getUploadUrl()).isEqualTo("http://put");
        assertThat(resp.getAssetType()).isEqualTo("DOCUMENT");
    }

    @Test
    void commit_shouldWriteSourceFileId() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        book.setFormat("MARKDOWN");
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(ebookBookRepository.findBySourceFileId("doc-9")).thenReturn(Optional.empty());
        when(liteMediaClient.commit(any())).thenReturn(new MediaInfo(
                "doc-9", "DOCUMENT", "a.md", "text/markdown",
                null, null, "PROCESSING", null, null));
        when(ebookBookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitEbookRequest req = new CommitEbookRequest();
        req.setFileId("doc-9");
        req.setFilename("a.md");
        req.setSplitRule("MARKDOWN");

        EbookAdminResponse resp = service.commit(5L, req);
        assertThat(resp.getSourceFileId()).isEqualTo("doc-9");

        ArgumentCaptor<CommitMediaRequest> captor = ArgumentCaptor.forClass(CommitMediaRequest.class);
        verify(liteMediaClient).commit(captor.capture());
        assertThat(captor.getValue().assetType()).isEqualTo("DOCUMENT");
        assertThat(captor.getValue().splitRule()).isEqualTo("MARKDOWN");
    }

    @Test
    void commit_shouldRejectSourceBoundToOtherEbook() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        EbookBook other = new EbookBook();
        other.setId(6L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(ebookBookRepository.findBySourceFileId("doc-9")).thenReturn(Optional.of(other));

        CommitEbookRequest req = new CommitEbookRequest();
        req.setFileId("doc-9");
        req.setFilename("a.md");

        assertThatThrownBy(() -> service.commit(5L, req)).isInstanceOf(BusinessException.class);
        verify(liteMediaClient, never()).commit(any());
    }

    @Test
    void syncChapters_mockWithoutSource_shouldBindMockDoc() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(ebookBookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ebookTocSyncService.syncByEbookId(5L)).thenReturn(3);

        SyncChaptersResponse resp = service.syncChapters(5L);
        assertThat(resp.getChapterCount()).isEqualTo(3);
        assertThat(resp.getSourceFileId()).isEqualTo(MockLiteMediaClient.SOURCE_FILE_ID);
        assertThat(book.getSourceFileId()).isEqualTo(MockLiteMediaClient.SOURCE_FILE_ID);
        verify(ebookTocSyncService).syncByEbookId(eq(5L));
    }

    @Test
    void syncChapters_realWithoutSource_shouldFail() {
        liteMediaProperties.setMock(false);
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.syncChapters(5L))
                .isInstanceOf(ReaderException.class);
        verify(ebookTocSyncService, never()).syncByEbookId(any());
    }

    @Test
    void uploadSignature_missingEbook_should404() {
        when(ebookBookRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.uploadSignature(404L))
                .isInstanceOf(ReaderException.class);
    }

    @Test
    void uploadSignatureMultipart_shouldForceDocument() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));
        when(liteMediaClient.createMultipartUploadSignature(any()))
                .thenReturn(new MultipartUploadSignature(
                        "doc-m", "raw/doc-m/source.bin", "uid",
                        10_485_760L, 2,
                        List.of(new PartUrl(1, "http://p1"), new PartUrl(2, "http://p2")),
                        1710000000L));

        MultipartUploadSignatureRequest req = new MultipartUploadSignatureRequest();
        req.setFilename("big.md");
        req.setContentLength(20_000_000L);
        req.setPartSize(10_485_760L);

        MultipartUploadSignatureResponse resp = service.uploadSignatureMultipart(5L, req);
        assertThat(resp.getEbookId()).isEqualTo(5L);
        assertThat(resp.getFileId()).isEqualTo("doc-m");
        assertThat(resp.getPartCount()).isEqualTo(2);
        assertThat(resp.getAssetType()).isEqualTo("DOCUMENT");

        ArgumentCaptor<MultipartUploadRequest> captor = ArgumentCaptor.forClass(MultipartUploadRequest.class);
        verify(liteMediaClient).createMultipartUploadSignature(captor.capture());
        assertThat(captor.getValue().assetType()).isEqualTo("DOCUMENT");
    }

    @Test
    void completeMultipart_shouldDelegate() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));

        CompleteMultipartUploadRequest req = new CompleteMultipartUploadRequest();
        req.setUploadId("uid");
        req.setParts(List.of(new PartEtag(1, "\"a\"")));

        service.completeMultipart(5L, "doc-m", req);
        verify(liteMediaClient).completeMultipart(eq("doc-m"), any(CompleteMultipartRequest.class));
    }

    @Test
    void abortMultipart_shouldDelegate() {
        EbookBook book = new EbookBook();
        book.setId(5L);
        when(ebookBookRepository.findById(5L)).thenReturn(Optional.of(book));

        AbortMultipartUploadRequest req = new AbortMultipartUploadRequest();
        req.setUploadId("uid");
        service.abortMultipart(5L, "doc-m", req);
        verify(liteMediaClient).abortMultipart(eq("doc-m"), any(AbortMultipartRequest.class));
    }
}
