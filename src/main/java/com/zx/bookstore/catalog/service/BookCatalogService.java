package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.*;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.entity.BookCategory;
import com.zx.bookstore.catalog.repository.BookCategoryRepository;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.exception.BookstoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookCatalogService {

    private final BookRepository bookRepository;
    private final BookCategoryRepository categoryRepository;
    private final BookRedisService bookRedisService;

    public PageResult<BookResponse> listBooks(Long categoryId, String keyword, long page, long size) {
        List<Book> books = bookRepository.pageEnabled(categoryId, keyword, page, size);
        long total = bookRepository.countEnabled(categoryId, keyword);
        Map<Long, String> categoryNameById = categoryNameById(books);
        List<BookResponse> records = books.stream()
                .map(b -> toResponse(b, categoryNameById.get(b.getCategoryId())))
                .collect(Collectors.toList());
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    public BookResponse getBookDetail(Long id) {
        Optional<BookResponse> cached = bookRedisService.get(id);
        if (cached.isPresent()) {
            log.debug("book detail cache hit, id={}", id);
            return cached.get();
        }
        Book book = bookRepository.findEnabledById(id)
                .orElseThrow(BookstoreException::bookNotFound);
        String categoryName = categoryRepository.findById(book.getCategoryId())
                .map(BookCategory::getName)
                .orElse(null);
        BookResponse response = toResponse(book, categoryName);
        bookRedisService.put(response);
        return response;
    }

    public List<BookCategoryResponse> listCategories() {
        return categoryRepository.listEnabled().stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookResponse createBook(CreateBookRequest req) {
        validateCreateRequest(req);
        BookCategory category = categoryRepository.findEnabledById(req.getCategoryId())
                .orElseThrow(BookstoreException::categoryNotFound);
        Book book = new Book();
        applyCreate(book, req);
        book.setStatus(1);
        bookRepository.save(book);
        return toResponse(book, category.getName());
    }

    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest req) {
        Book book = bookRepository.findById(id)
                .orElseThrow(BookstoreException::bookNotFound);
        if (req.getCategoryId() != null) {
            categoryRepository.findEnabledById(req.getCategoryId())
                    .orElseThrow(BookstoreException::categoryNotFound);
            book.setCategoryId(req.getCategoryId());
        }
        if (StringUtils.hasText(req.getIsbn())) {
            book.setIsbn(req.getIsbn());
        }
        if (StringUtils.hasText(req.getTitle())) {
            book.setTitle(req.getTitle());
        }
        if (req.getAuthor() != null) {
            book.setAuthor(req.getAuthor());
        }
        if (req.getCoverUrl() != null) {
            book.setCoverUrl(req.getCoverUrl());
        }
        if (req.getPrice() != null) {
            book.setPrice(req.getPrice());
        }
        if (req.getSaleStock() != null) {
            book.setSaleStock(req.getSaleStock());
        }
        if (req.getBorrowStock() != null) {
            book.setBorrowStock(req.getBorrowStock());
        }
        if (req.getBorrowDays() != null) {
            book.setBorrowDays(req.getBorrowDays());
        }
        if (req.getStatus() != null) {
            book.setStatus(req.getStatus());
        }
        if (req.getDescription() != null) {
            book.setDescription(req.getDescription());
        }
        bookRepository.save(book);
        bookRedisService.evict(id);
        String categoryName = categoryRepository.findById(book.getCategoryId())
                .map(BookCategory::getName)
                .orElse(null);
        return toResponse(book, categoryName);
    }

    @Transactional
    public void offShelf(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(BookstoreException::bookNotFound);
        if (book.getStatus() != null && book.getStatus() == 0) {
            bookRedisService.evict(id);
            return;
        }
        bookRepository.updateStatus(id, 0);
        bookRedisService.evict(id);
    }

    @Transactional
    public BookCategoryResponse createCategory(CreateBookCategoryRequest req) {
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (categoryRepository.existsByName(req.getName().trim())) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        BookCategory category = new BookCategory();
        category.setName(req.getName().trim());
        category.setSort(req.getSort() == null ? 0 : req.getSort());
        category.setStatus(1);
        categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    private void validateCreateRequest(CreateBookRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getCategoryId() == null) {
            throw new IllegalArgumentException("categoryId 不能为空");
        }
        if (!StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (req.getPrice() == null) {
            throw new IllegalArgumentException("price 不能为空");
        }
    }

    private Map<Long, String> categoryNameById(List<Book> books) {
        List<Long> categoryIds = books.stream()
                .map(Book::getCategoryId)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.listEnabled().stream()
                .collect(Collectors.toMap(BookCategory::getId, BookCategory::getName));
    }

    private void applyCreate(Book book, CreateBookRequest req) {
        book.setCategoryId(req.getCategoryId());
        book.setIsbn(req.getIsbn());
        book.setTitle(req.getTitle());
        book.setAuthor(req.getAuthor());
        book.setCoverUrl(req.getCoverUrl());
        book.setPrice(req.getPrice());
        book.setSaleStock(req.getSaleStock() == null ? 0 : req.getSaleStock());
        book.setBorrowStock(req.getBorrowStock() == null ? 0 : req.getBorrowStock());
        book.setBorrowDays(req.getBorrowDays() == null ? 30 : req.getBorrowDays());
        book.setDescription(req.getDescription());
    }

    private BookResponse toResponse(Book book, String categoryName) {
        BookResponse resp = new BookResponse();
        resp.setId(book.getId());
        resp.setCategoryId(book.getCategoryId());
        resp.setCategoryName(categoryName);
        resp.setIsbn(book.getIsbn());
        resp.setTitle(book.getTitle());
        resp.setAuthor(book.getAuthor());
        resp.setCoverUrl(book.getCoverUrl());
        resp.setPrice(book.getPrice());
        resp.setSaleStock(book.getSaleStock());
        resp.setBorrowStock(book.getBorrowStock());
        resp.setBorrowDays(book.getBorrowDays());
        resp.setStatus(book.getStatus());
        resp.setDescription(book.getDescription());
        return resp;
    }

    private BookCategoryResponse toCategoryResponse(BookCategory category) {
        BookCategoryResponse resp = new BookCategoryResponse();
        resp.setId(category.getId());
        resp.setName(category.getName());
        resp.setSort(category.getSort());
        resp.setStatus(category.getStatus());
        return resp;
    }
}
