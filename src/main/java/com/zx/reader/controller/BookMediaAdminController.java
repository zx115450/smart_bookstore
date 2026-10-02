package com.zx.reader.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.reader.dto.BindBookMediaRequest;
import com.zx.reader.dto.BookMediaRefResponse;
import com.zx.reader.service.BookMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：图书配套视频绑定（B6）。
 */
@RestController
@RequestMapping("/api/admin/books/{bookId}/media")
@RequiredArgsConstructor
public class BookMediaAdminController {

    private final BookMediaService bookMediaService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<BookMediaRefResponse> bind(
            @PathVariable Long bookId,
            @RequestBody BindBookMediaRequest request
    ) {
        return ApiResponse.ok(bookMediaService.bind(bookId, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<List<BookMediaRefResponse>> list(@PathVariable Long bookId) {
        return ApiResponse.ok(bookMediaService.listAdmin(bookId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{refId}")
    public ApiResponse<Void> unbind(@PathVariable Long bookId, @PathVariable Long refId) {
        bookMediaService.unbind(bookId, refId);
        return ApiResponse.ok(null);
    }
}
