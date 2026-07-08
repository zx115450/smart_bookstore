package com.zx.bookstore.catalog.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.mapper.BookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookRepository {

    private final BookMapper mapper;

    public Optional<Book> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<Book> findEnabledById(Long id) {
        return findById(id).filter(b -> b.getStatus() != null && b.getStatus() == 1);
    }

    public List<Book> pageEnabled(Long categoryId, String keyword, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<Book>lambdaQuery()
                .eq(Book::getStatus, 1)
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Book::getTitle, keyword)
                .orderByDesc(Book::getId)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        return mapper.selectList(wrapper);
    }

    public long countEnabled(Long categoryId, String keyword) {
        var wrapper = Wrappers.<Book>lambdaQuery()
                .eq(Book::getStatus, 1)
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Book::getTitle, keyword);
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public List<Book> pageAll(Long categoryId, String keyword, Integer status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        var wrapper = Wrappers.<Book>lambdaQuery()
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Book::getTitle, keyword)
                .eq(status != null, Book::getStatus, status)
                .orderByDesc(Book::getId)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        return mapper.selectList(wrapper);
    }

    public long countAll(Long categoryId, String keyword, Integer status) {
        var wrapper = Wrappers.<Book>lambdaQuery()
                .eq(categoryId != null, Book::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Book::getTitle, keyword)
                .eq(status != null, Book::getStatus, status);
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    public Book save(Book book) {
        LocalDateTime now = LocalDateTime.now();
        if (book.getId() == null) {
            if (book.getCreatedAt() == null) {
                book.setCreatedAt(now);
            }
            book.setUpdatedAt(now);
            mapper.insert(book);
            return book;
        }
        book.setUpdatedAt(now);
        mapper.updateById(book);
        return book;
    }

    public int updateStatus(Long id, int status) {
        Book update = new Book();
        update.setId(id);
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        return mapper.updateById(update);
    }

    public Optional<Book> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectByIdForUpdate(id));
    }

    public void saveBorrowStock(Book book) {
        Book update = new Book();
        update.setId(book.getId());
        update.setBorrowStock(book.getBorrowStock());
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    public boolean deductSaleStock(Long bookId, int quantity) {
        if (bookId == null || quantity <= 0) {
            return false;
        }
        return mapper.deductSaleStock(bookId, quantity) > 0;
    }
}
