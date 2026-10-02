package com.zx.reader.controller;

import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.common.dto.ApiResponse;
import com.zx.reader.dto.BookMediaPlayResponse;
import com.zx.reader.dto.BookMediaRefResponse;
import com.zx.reader.service.BookMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端：图书配套视频列表与播放签名（B6）。
 */
@RestController
@RequestMapping("/api/books/{bookId}/media")
@RequiredArgsConstructor
public class BookMediaController {

    private final BookMediaService bookMediaService;

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping
    public ApiResponse<List<BookMediaRefResponse>> list(@PathVariable Long bookId) {
        return ApiResponse.ok(bookMediaService.listUser(bookId));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{refId}/play")
    public ApiResponse<BookMediaPlayResponse> play(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long bookId,
            @PathVariable Long refId
    ) {
        return ApiResponse.ok(bookMediaService.play(
                principal.userId(), principal.roles(), bookId, refId));
    }
}
