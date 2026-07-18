package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.*;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.entity.BookCategory;
import com.zx.bookstore.catalog.entity.Bookshelf;
import com.zx.bookstore.catalog.repository.BookCategoryRepository;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.repository.BookshelfRepository;
import com.zx.bookstore.catalog.support.ShelfLocationSupport;
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
    private final BookshelfRepository bookshelfRepository;
    private final BookRedisService bookRedisService;
    private final BookStockLogService bookStockLogService;
    /** 布隆过滤器：用户详情读路径防缓存穿透（管理端详情不走布隆）。 */
    private final BookBloomRedisService bookBloomRedisService;

    public PageResult<BookResponse> listBooks(Long categoryId, String keyword, long page, long size) {
        List<Book> books = bookRepository.pageEnabled(categoryId, keyword, page, size);
        long total = bookRepository.countEnabled(categoryId, keyword);
        Map<Long, String> categoryNameById = categoryNameById(books);
        Map<Long, Bookshelf> bookshelfById = bookshelfById(books);
        List<BookResponse> records = books.stream()
                .map(b -> toResponse(b, categoryNameById.get(b.getCategoryId()), bookshelfById.get(b.getBookshelfId())))
                .collect(Collectors.toList());
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    public BookResponse getBookDetail(Long id) {
        // 布隆说不存在 → 直接 404，不打 Redis 详情缓存、不打 MySQL
        if (!bookBloomRedisService.mightContain(id)) {
            throw BookstoreException.bookNotFound();
        }
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
        Bookshelf bookshelf = book.getBookshelfId() == null
                ? null
                : bookshelfRepository.findById(book.getBookshelfId()).orElse(null);
        BookResponse response = toResponse(book, categoryName, bookshelf);
        bookRedisService.put(response);
        return response;
    }

    public List<BookCategoryResponse> listCategories() {
        return categoryRepository.listEnabled().stream()
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    public PageResult<BookResponse> listBooksAdmin(Long categoryId, String keyword, Integer status, long page, long size) {
        List<Book> books = bookRepository.pageAll(categoryId, keyword, status, page, size);
        long total = bookRepository.countAll(categoryId, keyword, status);
        Map<Long, String> categoryNameById = categoryNameById(books);
        Map<Long, Bookshelf> bookshelfById = bookshelfById(books);
        List<BookResponse> records = books.stream()
                .map(b -> toResponse(b, categoryNameById.get(b.getCategoryId()), bookshelfById.get(b.getBookshelfId())))
                .collect(Collectors.toList());
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    public BookResponse getBookDetailAdmin(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(BookstoreException::bookNotFound);
        String categoryName = categoryRepository.findById(book.getCategoryId())
                .map(BookCategory::getName)
                .orElse(null);
        Bookshelf bookshelf = book.getBookshelfId() == null
                ? null
                : bookshelfRepository.findById(book.getBookshelfId()).orElse(null);
        return toResponse(book, categoryName, bookshelf);
    }

    public List<BookCategoryResponse> listCategoriesAdmin() {
        return categoryRepository.listAll().stream()
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
        validateBorrowShelfLocation(book);
        book.setStatus(1);
        bookRepository.save(book);
        // 增量写入布隆，避免重启前新建的书被误判为不存在
        bookBloomRedisService.add(book.getId());
        Bookshelf bookshelf = book.getBookshelfId() == null
                ? null
                : bookshelfRepository.findById(book.getBookshelfId()).orElse(null);
        return toResponse(book, category.getName(), bookshelf);
    }

    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest req, Long operatorId) {
        Book book = bookRepository.findById(id)
                .orElseThrow(BookstoreException::bookNotFound);
        Integer oldSaleStock = book.getSaleStock();
        Integer oldBorrowStock = book.getBorrowStock();
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
        if (req.getBookshelfId() != null) {
            book.setBookshelfId(req.getBookshelfId());
        }
        if (req.getShelfLayer() != null) {
            book.setShelfLayer(req.getShelfLayer());
        }
        validateBorrowShelfLocation(book);
        bookRepository.save(book);
        recordAdminStockAdjust(id, oldSaleStock, book.getSaleStock(), oldBorrowStock, book.getBorrowStock(), operatorId);
        bookRedisService.evict(id);
        String categoryName = categoryRepository.findById(book.getCategoryId())
                .map(BookCategory::getName)
                .orElse(null);
        Bookshelf bookshelf = book.getBookshelfId() == null
                ? null
                : bookshelfRepository.findById(book.getBookshelfId()).orElse(null);
        return toResponse(book, categoryName, bookshelf);
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

    @Transactional
    public BookCategoryResponse updateCategory(Long id, UpdateBookCategoryRequest req) {
        BookCategory category = categoryRepository.findById(id)
                .orElseThrow(BookstoreException::categoryNotFound);
        if (req != null) {
            if (StringUtils.hasText(req.getName())) {
                String name = req.getName().trim();
                if (categoryRepository.existsByNameExceptId(name, id)) {
                    throw new IllegalArgumentException("分类名称已存在");
                }
                category.setName(name);
            }
            if (req.getSort() != null) {
                category.setSort(req.getSort());
            }
            if (req.getStatus() != null) {
                category.setStatus(req.getStatus());
            }
        }
        categoryRepository.save(category);
        return toCategoryResponse(category);
    }

    @Transactional
    public void disableCategory(Long id) {
        BookCategory category = categoryRepository.findById(id)
                .orElseThrow(BookstoreException::categoryNotFound);
        if (category.getStatus() != null && category.getStatus() == 0) {
            return;
        }
        categoryRepository.updateStatus(id, 0);
    }

    private void recordAdminStockAdjust(Long bookId, Integer oldSaleStock, Integer newSaleStock,
                                        Integer oldBorrowStock, Integer newBorrowStock, Long operatorId) {
        int oldSale = oldSaleStock == null ? 0 : oldSaleStock;
        int newSale = newSaleStock == null ? 0 : newSaleStock;
        if (newSale != oldSale) {
            int delta = Math.abs(newSale - oldSale);
            bookStockLogService.recordAdminAdjust(
                    bookId, delta, operatorId,
                    "sale_stock: " + oldSale + " -> " + newSale);
        }
        int oldBorrow = oldBorrowStock == null ? 0 : oldBorrowStock;
        int newBorrow = newBorrowStock == null ? 0 : newBorrowStock;
        if (newBorrow != oldBorrow) {
            int delta = Math.abs(newBorrow - oldBorrow);
            bookStockLogService.recordAdminAdjust(
                    bookId, delta, operatorId,
                    "borrow_stock: " + oldBorrow + " -> " + newBorrow);
        }
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
        return categoryRepository.findNamesByIds(categoryIds);
    }

    private Map<Long, Bookshelf> bookshelfById(List<Book> books) {
        List<Long> bookshelfIds = books.stream()
                .map(Book::getBookshelfId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (bookshelfIds.isEmpty()) {
            return Map.of();
        }
        return bookshelfRepository.findByIds(bookshelfIds);
    }

    private void validateBorrowShelfLocation(Book book) {
        int borrowStock = book.getBorrowStock() == null ? 0 : book.getBorrowStock();
        if (borrowStock <= 0) {
            return;
        }
        if (book.getBookshelfId() == null) {
            throw new IllegalArgumentException("可借图书须配置书架");
        }
        if (book.getShelfLayer() == null || book.getShelfLayer() <= 0) {
            throw new IllegalArgumentException("可借图书须配置书架层数（shelfLayer 为正整数）");
        }
        bookshelfRepository.findEnabledById(book.getBookshelfId())
                .orElseThrow(BookstoreException::bookshelfNotFound);
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
        book.setBookshelfId(req.getBookshelfId());
        book.setShelfLayer(req.getShelfLayer());
    }

    private BookResponse toResponse(Book book, String categoryName, Bookshelf bookshelf) {
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
        resp.setBookshelfId(book.getBookshelfId());
        resp.setShelfLayer(book.getShelfLayer());
        if (bookshelf != null) {
            resp.setBookshelfFloor(bookshelf.getFloor());
            resp.setBookshelfCode(bookshelf.getCode());
            resp.setShelfLocation(ShelfLocationSupport.format(bookshelf, book.getShelfLayer()));
        }
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
