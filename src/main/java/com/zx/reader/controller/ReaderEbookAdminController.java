package com.zx.reader.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.reader.dto.AbortMultipartUploadRequest;
import com.zx.reader.dto.CommitEbookRequest;
import com.zx.reader.dto.CompleteMultipartUploadRequest;
import com.zx.reader.dto.CreateEbookRequest;
import com.zx.reader.dto.EbookAdminResponse;
import com.zx.reader.dto.MultipartUploadSignatureRequest;
import com.zx.reader.dto.MultipartUploadSignatureResponse;
import com.zx.reader.dto.SyncChaptersResponse;
import com.zx.reader.dto.UploadSignatureResponse;
import com.zx.reader.service.EbookAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：线上书导入（B2）。鉴权在书城；浏览器只拿预签名 URL 直传 MinIO。
 */
@RestController
@RequestMapping("/api/reader/admin/ebooks")
@RequiredArgsConstructor
public class ReaderEbookAdminController {

    private final EbookAdminService ebookAdminService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<EbookAdminResponse> create(@RequestBody CreateEbookRequest request) {
        return ApiResponse.ok(ebookAdminService.create(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<EbookAdminResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(ebookAdminService.get(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/upload-signature")
    public ApiResponse<UploadSignatureResponse> uploadSignature(@PathVariable Long id) {
        return ApiResponse.ok(ebookAdminService.uploadSignature(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/upload-signature/multipart")
    public ApiResponse<MultipartUploadSignatureResponse> uploadSignatureMultipart(
            @PathVariable Long id,
            @RequestBody MultipartUploadSignatureRequest request
    ) {
        return ApiResponse.ok(ebookAdminService.uploadSignatureMultipart(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/uploads/{fileId}/complete")
    public ApiResponse<Void> completeMultipart(
            @PathVariable Long id,
            @PathVariable String fileId,
            @RequestBody CompleteMultipartUploadRequest request
    ) {
        ebookAdminService.completeMultipart(id, fileId, request);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/uploads/{fileId}/abort")
    public ApiResponse<Void> abortMultipart(
            @PathVariable Long id,
            @PathVariable String fileId,
            @RequestBody AbortMultipartUploadRequest request
    ) {
        ebookAdminService.abortMultipart(id, fileId, request);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/commit")
    public ApiResponse<EbookAdminResponse> commit(
            @PathVariable Long id,
            @RequestBody CommitEbookRequest request
    ) {
        return ApiResponse.ok(ebookAdminService.commit(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/sync-chapters")
    public ApiResponse<SyncChaptersResponse> syncChapters(@PathVariable Long id) {
        return ApiResponse.ok(ebookAdminService.syncChapters(id));
    }
}
