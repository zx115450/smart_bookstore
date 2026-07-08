package com.zx.bookstore.catalog.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.bookstore.catalog.dto.BookCategoryResponse;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.service.BookCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BookController {

    private final BookCatalogService bookCatalogService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/books")
    public ApiResponse<PageResult<BookResponse>> listBooks(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(bookCatalogService.listBooks(categoryId, keyword, page, size));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/books/{id}")
    public ApiResponse<BookResponse> getBookDetail(@PathVariable Long id) {
        return ApiResponse.ok(bookCatalogService.getBookDetail(id));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/book-categories")
    public ApiResponse<List<BookCategoryResponse>> listCategories() {
        return ApiResponse.ok(bookCatalogService.listCategories());
    }
}
