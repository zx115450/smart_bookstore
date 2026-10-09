package com.zx.reader.controller;

import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.common.dto.ApiResponse;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.ChapterContentResponse;
import com.zx.reader.dto.ChapterTocItemResponse;
import com.zx.reader.dto.EbookByBookResponse;
import com.zx.reader.dto.ReadingProgressResponse;
import com.zx.reader.dto.UpdateProgressRequest;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.service.EbookReaderService;
import com.zx.reader.service.ReadingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户侧试看 / 读章 / 进度 BFF（B3～B4）。
 */
@RestController
@RequestMapping("/api/reader/ebooks")
@RequiredArgsConstructor
public class ReaderEbookController {

    private final EbookReaderService ebookReaderService;
    private final ReadingProgressService readingProgressService;
    private final EbookBookRepository ebookBookRepository;

    /**
     * 按实体书查绑定的上架线上书（须写在 /{ebookId}/** 之前，避免 by-book 被当成 ebookId）。
     */
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/by-book/{bookId}")
    public ApiResponse<EbookByBookResponse> findByBook(@PathVariable Long bookId) {
        return ebookBookRepository.findEnabledByBookId(bookId)
                .map(e -> ApiResponse.ok(new EbookByBookResponse(e.getId(), e.getBookId(), e.getTitle())))
                .orElseThrow(ReaderException::ebookNotFound);
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{ebookId}/chapters")
    public ApiResponse<List<ChapterTocItemResponse>> listChapters(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long ebookId
    ) {
        return ApiResponse.ok(ebookReaderService.listChapters(principal.userId(), ebookId, principal.roles()));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{ebookId}/chapters/{chapterNo}")
    public ApiResponse<ChapterContentResponse> getChapter(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long ebookId,
            @PathVariable Integer chapterNo
    ) {
        return ApiResponse.ok(ebookReaderService.getChapterContent(
                principal.userId(), ebookId, chapterNo, principal.roles()));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{ebookId}/progress")
    public ApiResponse<ReadingProgressResponse> getProgress(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long ebookId
    ) {
        return ApiResponse.ok(readingProgressService.getProgress(principal.userId(), ebookId));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PutMapping("/{ebookId}/progress")
    public ApiResponse<ReadingProgressResponse> updateProgress(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long ebookId,
            @RequestBody UpdateProgressRequest request
    ) {
        return ApiResponse.ok(readingProgressService.updateProgress(
                principal.userId(), ebookId, request, principal.roles()));
    }
}
