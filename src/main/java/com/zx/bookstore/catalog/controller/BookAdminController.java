package com.zx.bookstore.catalog.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.bookstore.catalog.dto.BookCategoryResponse;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.dto.CreateBookCategoryRequest;
import com.zx.bookstore.catalog.dto.CreateBookRequest;
import com.zx.bookstore.catalog.dto.UpdateBookRequest;
import com.zx.bookstore.catalog.service.BookCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BookAdminController {

    private final BookCatalogService bookCatalogService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/books")
    public ApiResponse<BookResponse> createBook(@RequestBody CreateBookRequest req) {
        return ApiResponse.ok(bookCatalogService.createBook(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/books/{id}")
    public ApiResponse<BookResponse> updateBook(
            @PathVariable Long id,
            @RequestBody UpdateBookRequest req
    ) {
        return ApiResponse.ok(bookCatalogService.updateBook(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/books/{id}")
    public ApiResponse<Void> offShelf(@PathVariable Long id) {
        bookCatalogService.offShelf(id);
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/book-categories")
    public ApiResponse<BookCategoryResponse> createCategory(@RequestBody CreateBookCategoryRequest req) {
        return ApiResponse.ok(bookCatalogService.createCategory(req));
    }
}
