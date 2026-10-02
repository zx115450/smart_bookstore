package com.zx.reader.controller;

import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.common.dto.ApiResponse;
import com.zx.reader.dto.CreateNoteRequest;
import com.zx.reader.dto.NoteResponse;
import com.zx.reader.dto.UpdateNoteRequest;
import com.zx.reader.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阅读笔记 CRUD（B4）。
 */
@RestController
@RequestMapping("/api/reader/notes")
@RequiredArgsConstructor
public class ReaderNoteController {

    private final NoteService noteService;

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping
    public ApiResponse<NoteResponse> create(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody CreateNoteRequest request
    ) {
        return ApiResponse.ok(noteService.create(principal.userId(), request));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping
    public ApiResponse<List<NoteResponse>> list(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestParam(required = false) Long ebookId
    ) {
        return ApiResponse.ok(noteService.list(principal.userId(), ebookId));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{noteId}")
    public ApiResponse<NoteResponse> get(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long noteId
    ) {
        return ApiResponse.ok(noteService.get(principal.userId(), noteId));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PutMapping("/{noteId}")
    public ApiResponse<NoteResponse> update(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long noteId,
            @RequestBody UpdateNoteRequest request
    ) {
        return ApiResponse.ok(noteService.update(principal.userId(), noteId, request));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> delete(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long noteId
    ) {
        noteService.delete(principal.userId(), noteId);
        return ApiResponse.ok(null);
    }
}
