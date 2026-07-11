package com.zx.bookstore.catalog.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.catalog.dto.*;
import com.zx.bookstore.catalog.service.BookCatalogService;
import com.zx.bookstore.catalog.service.BookStockLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BookAdminController {

    private final BookCatalogService bookCatalogService;
    private final BookStockLogService bookStockLogService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/books")
    public ApiResponse<PageResult<BookResponse>> listBooks(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.ok(bookCatalogService.listBooksAdmin(categoryId, keyword, status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/books/{id}")
    public ApiResponse<BookResponse> getBook(@PathVariable Long id) {
        return ApiResponse.ok(bookCatalogService.getBookDetailAdmin(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/books")
    public ApiResponse<BookResponse> createBook(@RequestBody CreateBookRequest req) {
        return ApiResponse.ok(bookCatalogService.createBook(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/books/{id}")
    public ApiResponse<BookResponse> updateBook(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody UpdateBookRequest req
    ) {
        return ApiResponse.ok(bookCatalogService.updateBook(id, req, principal.userId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/books/{bookId}/stock-logs")
    public ApiResponse<PageResult<BookStockLogResponse>> listStockLogs(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(bookStockLogService.listByBookId(bookId, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/books/{id}")
    public ApiResponse<Void> offShelf(@PathVariable Long id) {
        bookCatalogService.offShelf(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/book-categories")
    public ApiResponse<List<BookCategoryResponse>> listCategories() {
        return ApiResponse.ok(bookCatalogService.listCategoriesAdmin());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/book-categories")
    public ApiResponse<BookCategoryResponse> createCategory(@RequestBody CreateBookCategoryRequest req) {
        return ApiResponse.ok(bookCatalogService.createCategory(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/book-categories/{id}")
    public ApiResponse<BookCategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody UpdateBookCategoryRequest req
    ) {
        return ApiResponse.ok(bookCatalogService.updateCategory(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/book-categories/{id}")
    public ApiResponse<Void> disableCategory(@PathVariable Long id) {
        bookCatalogService.disableCategory(id);
        return ApiResponse.ok(null);
    }
}
