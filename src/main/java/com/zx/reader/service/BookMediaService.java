package com.zx.reader.service;

import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.exception.BookstoreException;
import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;
import com.zx.media.client.BookstoreMediaProperties;
import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.PlaySignature;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.BindBookMediaRequest;
import com.zx.reader.dto.BookMediaPlayResponse;
import com.zx.reader.dto.BookMediaRefResponse;
import com.zx.reader.entity.BookMediaRef;
import com.zx.reader.repository.BookMediaRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 图书配套视频绑定与播放（B6）。
 */
@Service
@RequiredArgsConstructor
public class BookMediaService {

    private final BookRepository bookRepository;
    private final BookMediaRefRepository bookMediaRefRepository;
    private final LiteMediaClient liteMediaClient;
    private final MediaAccessService mediaAccessService;
    private final BookstoreMediaProperties mediaProperties;

    @Transactional
    public BookMediaRefResponse bind(Long bookId, BindBookMediaRequest request) {
        requireBook(bookId);
        if (request == null || request.getFileId() == null || request.getFileId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fileId 不能为空");
        }
        String fileId = request.getFileId().trim();
        MediaInfo info = requireFinishedVideo(fileId);

        BookMediaRef existing = bookMediaRefRepository.findByBookIdAndFileId(bookId, fileId).orElse(null);
        if (existing != null && existing.getStatus() != null && existing.getStatus() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该视频已绑定到本书");
        }

        BookMediaRef ref;
        if (existing != null) {
            ref = existing;
            ref.setStatus(1);
        } else {
            ref = new BookMediaRef();
            ref.setBookId(bookId);
            ref.setFileId(fileId);
            ref.setStatus(1);
        }
        ref.setTitle(resolveTitle(request.getTitle(), info));
        ref.setMediaType(resolveMediaType(request.getMediaType()));
        ref.setPreviewSeconds(resolvePreviewSeconds(request.getPreviewSeconds()));
        ref.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return toResponse(bookMediaRefRepository.save(ref));
    }

    public List<BookMediaRefResponse> listAdmin(Long bookId) {
        requireBook(bookId);
        return bookMediaRefRepository.listByBookId(bookId).stream().map(this::toResponse).toList();
    }

    public List<BookMediaRefResponse> listUser(Long bookId) {
        bookRepository.findEnabledById(bookId).orElseThrow(BookstoreException::bookNotFound);
        return bookMediaRefRepository.listByBookId(bookId).stream().map(this::toUserResponse).toList();
    }

    @Transactional
    public void unbind(Long bookId, Long refId) {
        requireBook(bookId);
        if (!bookMediaRefRepository.softDelete(bookId, refId)) {
            throw ReaderException.mediaRefNotFound();
        }
    }

    public BookMediaPlayResponse play(Long userId, Collection<String> roles, Long bookId, Long refId) {
        bookRepository.findEnabledById(bookId).orElseThrow(BookstoreException::bookNotFound);
        BookMediaRef ref = bookMediaRefRepository.findActiveByBookIdAndId(bookId, refId)
                .orElseThrow(ReaderException::mediaRefNotFound);

        boolean fullAccess = mediaAccessService.canWatchFullMedia(userId, bookId, roles);
        int previewSeconds = ref.getPreviewSeconds() == null ? 0 : ref.getPreviewSeconds();
        boolean preview = !fullAccess;
        if (preview && previewSeconds <= 0) {
            throw ReaderException.mediaPlayDenied();
        }

        PlaySignature signature;
        try {
            signature = liteMediaClient.getPlaySignature(ref.getFileId(), preview);
        } catch (ReaderException ex) {
            if (ex.getCode() == ErrorCode.MEDIA_CHAPTER_NOT_READY) {
                throw ReaderException.mediaNotReady("视频媒资不存在或尚未处理完成: " + ref.getFileId());
            }
            if (ex.getCode() == ErrorCode.MEDIA_UNAVAILABLE) {
                throw ex;
            }
            throw ReaderException.playSignFailed(ex.getMessage());
        }
        if (signature == null || signature.playUrl() == null || signature.playUrl().isBlank()) {
            throw ReaderException.playSignFailed("媒资未返回 playUrl");
        }

        BookMediaPlayResponse resp = new BookMediaPlayResponse();
        resp.setRefId(ref.getId());
        resp.setFileId(ref.getFileId());
        resp.setPlayUrl(signature.playUrl());
        resp.setSignature(signature.signature());
        resp.setExpireAt(signature.expireAt());
        resp.setPreview(preview);
        resp.setPreviewSeconds(preview ? previewSeconds : null);
        return resp;
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId).orElseThrow(BookstoreException::bookNotFound);
    }

    private MediaInfo requireFinishedVideo(String fileId) {
        MediaInfo info;
        try {
            info = liteMediaClient.getMedia(fileId);
        } catch (ReaderException ex) {
            if (ex.getCode() == ErrorCode.MEDIA_CHAPTER_NOT_READY) {
                throw ReaderException.mediaNotReady("媒资 fileId 不存在: " + fileId);
            }
            throw ex;
        }
        if (info == null) {
            throw ReaderException.mediaNotReady("媒资 fileId 不存在: " + fileId);
        }
        String assetType = info.assetType() == null ? "" : info.assetType().trim().toUpperCase(Locale.ROOT);
        if (!"VIDEO".equals(assetType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持绑定 VIDEO 类型媒资，当前: " + info.assetType());
        }
        String status = info.status() == null ? "" : info.status().trim().toUpperCase(Locale.ROOT);
        if (!"FINISHED".equals(status)) {
            throw ReaderException.mediaNotReady("媒资尚未处理完成，当前状态: " + info.status());
        }
        return info;
    }

    private int resolvePreviewSeconds(Integer requestValue) {
        if (requestValue != null) {
            return Math.max(0, requestValue);
        }
        return Math.max(0, mediaProperties.getDefaultPreviewSeconds());
    }

    private static String resolveMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return "INTRO";
        }
        return mediaType.trim().toUpperCase(Locale.ROOT);
    }

    private static String resolveTitle(String title, MediaInfo info) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (info != null && info.filename() != null && !info.filename().isBlank()) {
            return info.filename();
        }
        return null;
    }

    private BookMediaRefResponse toResponse(BookMediaRef ref) {
        BookMediaRefResponse resp = new BookMediaRefResponse();
        resp.setId(ref.getId());
        resp.setBookId(ref.getBookId());
        resp.setFileId(ref.getFileId());
        resp.setTitle(ref.getTitle());
        resp.setMediaType(ref.getMediaType());
        resp.setPreviewSeconds(ref.getPreviewSeconds());
        resp.setSortOrder(ref.getSortOrder());
        resp.setStatus(ref.getStatus());
        return resp;
    }

    /** 用户列表不下发 fileId（避免绕过鉴权直打媒资）；管理端仍可见。 */
    private BookMediaRefResponse toUserResponse(BookMediaRef ref) {
        BookMediaRefResponse resp = toResponse(ref);
        resp.setFileId(null);
        return resp;
    }
}
