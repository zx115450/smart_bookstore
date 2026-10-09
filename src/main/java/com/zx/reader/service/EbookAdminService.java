package com.zx.reader.service;

import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;
import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.LiteMediaProperties;
import com.zx.media.client.MockLiteMediaClient;
import com.zx.media.client.dto.AbortMultipartRequest;
import com.zx.media.client.dto.CommitMediaRequest;
import com.zx.media.client.dto.CompleteMultipartRequest;
import com.zx.media.client.dto.MultipartUploadRequest;
import com.zx.media.client.dto.MultipartUploadSignature;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 管理端：创建线上书、代领上传凭证（整对象 / multipart）、commit 写 source_file_id、补救同步 TOC。
 */
@Service
@RequiredArgsConstructor
public class EbookAdminService {

    private static final Set<String> ALLOWED_FORMATS = Set.of("MARKDOWN", "TXT");
    private static final Set<String> ALLOWED_SPLIT_RULES = Set.of("MARKDOWN", "TXT");

    private final EbookBookRepository ebookBookRepository;
    private final BookRepository bookRepository;
    private final LiteMediaClient liteMediaClient;
    private final LiteMediaProperties liteMediaProperties;
    private final EbookTocSyncService ebookTocSyncService;
    private final ReaderProperties readerProperties;

    @Transactional
    public EbookAdminResponse create(CreateEbookRequest req) {
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("title 必填");
        }
        if (req.getBookId() != null) {
            bookRepository.findById(req.getBookId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOOKSTORE_BOOK_NOT_FOUND, "实体书不存在"));
        }

        String format = normalizeFormat(req.getFormat());
        int preview = req.getPreviewChapters() != null
                ? Math.max(0, req.getPreviewChapters())
                : Math.max(0, readerProperties.getPreview().getDefaultChapters());

        EbookBook book = new EbookBook();
        book.setTitle(req.getTitle().trim());
        book.setAuthor(trimToNull(req.getAuthor()));
        book.setBookId(req.getBookId());
        book.setCoverUrl(trimToNull(req.getCoverUrl()));
        book.setFormat(format);
        book.setStatus(1);
        book.setPreviewMode("CHAPTER");
        book.setPreviewChapters(preview);
        book.setTotalChapters(0);
        book.setWordCount(0L);
        return toResponse(ebookBookRepository.save(book));
    }

    public UploadSignatureResponse uploadSignature(Long ebookId) {
        requireEbook(ebookId);
        UploadSignature sig = liteMediaClient.createUploadSignature("DOCUMENT");
        if (sig == null || !StringUtils.hasText(sig.fileId()) || !StringUtils.hasText(sig.uploadUrl())) {
            throw ReaderException.mediaUnavailable("媒资未返回上传凭证");
        }
        UploadSignatureResponse resp = new UploadSignatureResponse();
        resp.setEbookId(ebookId);
        resp.setFileId(sig.fileId());
        resp.setUploadUrl(sig.uploadUrl());
        resp.setObjectKey(sig.objectKey());
        resp.setExpireAt(sig.expireAt());
        resp.setAssetType("DOCUMENT");
        return resp;
    }

    /**
     * 大文件分片：代领各片预签名 URL；浏览器直传 MinIO 后需 complete 再 commit。
     */
    public MultipartUploadSignatureResponse uploadSignatureMultipart(Long ebookId,
                                                                     MultipartUploadSignatureRequest req) {
        requireEbook(ebookId);
        if (req == null || req.getContentLength() == null || req.getContentLength() <= 0) {
            throw new IllegalArgumentException("contentLength 必填且须 > 0");
        }
        long partSize = req.getPartSize() == null || req.getPartSize() <= 0
                ? 5L * 1024 * 1024
                : req.getPartSize();
        String filename = StringUtils.hasText(req.getFilename()) ? req.getFilename().trim() : "source.bin";
        String contentType = StringUtils.hasText(req.getContentType())
                ? req.getContentType().trim()
                : "application/octet-stream";

        MultipartUploadSignature sig = liteMediaClient.createMultipartUploadSignature(
                MultipartUploadRequest.document(filename, contentType, req.getContentLength(), partSize));
        if (sig == null || !StringUtils.hasText(sig.fileId()) || !StringUtils.hasText(sig.uploadId())) {
            throw ReaderException.mediaUnavailable("媒资未返回分片上传凭证");
        }

        MultipartUploadSignatureResponse resp = new MultipartUploadSignatureResponse();
        resp.setEbookId(ebookId);
        resp.setFileId(sig.fileId());
        resp.setObjectKey(sig.objectKey());
        resp.setUploadId(sig.uploadId());
        resp.setPartSize(sig.partSize());
        resp.setPartCount(sig.partCount());
        resp.setParts(sig.parts());
        resp.setExpireAt(sig.expireAt());
        resp.setAssetType("DOCUMENT");
        return resp;
    }

    public void completeMultipart(Long ebookId, String fileId, CompleteMultipartUploadRequest req) {
        requireEbook(ebookId);
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId 必填");
        }
        if (req == null || !StringUtils.hasText(req.getUploadId())) {
            throw new IllegalArgumentException("uploadId 必填");
        }
        if (req.getParts() == null || req.getParts().isEmpty()) {
            throw new IllegalArgumentException("parts 不能为空");
        }
        liteMediaClient.completeMultipart(
                fileId.trim(),
                new CompleteMultipartRequest(req.getUploadId().trim(), req.getParts()));
    }

    public void abortMultipart(Long ebookId, String fileId, AbortMultipartUploadRequest req) {
        requireEbook(ebookId);
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId 必填");
        }
        if (req == null || !StringUtils.hasText(req.getUploadId())) {
            throw new IllegalArgumentException("uploadId 必填");
        }
        liteMediaClient.abortMultipart(
                fileId.trim(),
                new AbortMultipartRequest(req.getUploadId().trim()));
    }

    @Transactional
    public EbookAdminResponse commit(Long ebookId, CommitEbookRequest req) {
        if (req == null || !StringUtils.hasText(req.getFileId())) {
            throw new IllegalArgumentException("fileId 必填");
        }
        if (!StringUtils.hasText(req.getFilename())) {
            throw new IllegalArgumentException("filename 必填");
        }
        EbookBook book = requireEbook(ebookId);
        String fileId = req.getFileId().trim();
        String splitRule = normalizeSplitRule(req.getSplitRule());

        ebookBookRepository.findBySourceFileId(fileId).ifPresent(existing -> {
            if (!existing.getId().equals(ebookId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "该媒资 DOCUMENT 已绑定其他线上书: " + existing.getId());
            }
        });

        liteMediaClient.commit(CommitMediaRequest.document(fileId, req.getFilename().trim(), splitRule));

        book.setSourceFileId(fileId);
        if ("TXT".equals(splitRule)) {
            book.setFormat("TXT");
        } else if ("MARKDOWN".equals(splitRule)) {
            book.setFormat("MARKDOWN");
        }
        return toResponse(ebookBookRepository.save(book));
    }

    /**
     * 补救同步 TOC。Mock 且尚未 commit 时自动绑定 {@code mock-doc-1}。
     */
    @Transactional
    public SyncChaptersResponse syncChapters(Long ebookId) {
        EbookBook book = requireEbook(ebookId);
        if (!StringUtils.hasText(book.getSourceFileId())) {
            if (!liteMediaProperties.isMock()) {
                throw ReaderException.chapterNotReady("线上书尚未绑定媒资 DOCUMENT，请先 commit");
            }
            book.setSourceFileId(MockLiteMediaClient.SOURCE_FILE_ID);
            ebookBookRepository.save(book);
        }
        int n = ebookTocSyncService.syncByEbookId(ebookId);
        EbookBook latest = requireEbook(ebookId);
        return new SyncChaptersResponse(ebookId, latest.getSourceFileId(), n);
    }

    public EbookAdminResponse get(Long ebookId) {
        return toResponse(requireEbook(ebookId));
    }

    private EbookBook requireEbook(Long ebookId) {
        return ebookBookRepository.findById(ebookId)
                .orElseThrow(ReaderException::ebookNotFound);
    }

    private static String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return "MARKDOWN";
        }
        String f = format.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_FORMATS.contains(f)) {
            throw new IllegalArgumentException("format 仅支持 MARKDOWN / TXT");
        }
        return f;
    }

    private static String normalizeSplitRule(String splitRule) {
        if (!StringUtils.hasText(splitRule)) {
            return "MARKDOWN";
        }
        String r = splitRule.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SPLIT_RULES.contains(r)) {
            throw new IllegalArgumentException("splitRule 仅支持 MARKDOWN / TXT");
        }
        return r;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static EbookAdminResponse toResponse(EbookBook book) {
        EbookAdminResponse resp = new EbookAdminResponse();
        resp.setId(book.getId());
        resp.setBookId(book.getBookId());
        resp.setTitle(book.getTitle());
        resp.setAuthor(book.getAuthor());
        resp.setCoverUrl(book.getCoverUrl());
        resp.setFormat(book.getFormat());
        resp.setStatus(book.getStatus());
        resp.setPreviewChapters(book.getPreviewChapters());
        resp.setTotalChapters(book.getTotalChapters());
        resp.setWordCount(book.getWordCount());
        resp.setSourceFileId(book.getSourceFileId());
        resp.setCreatedAt(book.getCreatedAt());
        resp.setUpdatedAt(book.getUpdatedAt());
        return resp;
    }
}
