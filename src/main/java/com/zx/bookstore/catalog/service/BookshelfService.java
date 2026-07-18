package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.*;
import com.zx.bookstore.catalog.entity.Bookshelf;
import com.zx.bookstore.catalog.repository.BookshelfRepository;
import com.zx.bookstore.exception.BookstoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookshelfService {

    private final BookshelfRepository bookshelfRepository;

    public List<BookshelfResponse> listEnabled(Integer floor) {
        return bookshelfRepository.listEnabled(floor).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PageResult<BookshelfResponse> listBookshelvesAdmin(Integer floor, Integer status, long page, long size) {
        List<BookshelfResponse> records = bookshelfRepository.pageAll(floor, status, page, size).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        long total = bookshelfRepository.countAll(floor, status);
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    @Transactional
    public BookshelfResponse createBookshelf(CreateBookshelfRequest req) {
        validateCreateRequest(req);
        Integer floor = req.getFloor();
        String code = req.getCode().trim();
        if (bookshelfRepository.existsByFloorAndCode(floor, code)) {
            throw new IllegalArgumentException("该楼层下书架编号已存在");
        }
        Bookshelf bookshelf = new Bookshelf();
        bookshelf.setFloor(floor);
        bookshelf.setCode(code);
        bookshelf.setStatus(1);
        bookshelfRepository.save(bookshelf);
        return toResponse(bookshelf);
    }

    @Transactional
    public BookshelfResponse updateBookshelf(Long id, UpdateBookshelfRequest req) {
        Bookshelf bookshelf = bookshelfRepository.findById(id)
                .orElseThrow(BookstoreException::bookshelfNotFound);
        if (req != null) {
            if (req.getFloor() != null) {
                bookshelf.setFloor(req.getFloor());
            }
            if (StringUtils.hasText(req.getCode())) {
                bookshelf.setCode(req.getCode().trim());
            }
            if (req.getStatus() != null) {
                bookshelf.setStatus(req.getStatus());
            }
            if (bookshelfRepository.existsByFloorAndCodeExceptId(
                    bookshelf.getFloor(), bookshelf.getCode(), id)) {
                throw new IllegalArgumentException("该楼层下书架编号已存在");
            }
        }
        bookshelfRepository.save(bookshelf);
        return toResponse(bookshelf);
    }

    @Transactional
    public void disableBookshelf(Long id) {
        Bookshelf bookshelf = bookshelfRepository.findById(id)
                .orElseThrow(BookstoreException::bookshelfNotFound);
        if (bookshelf.getStatus() != null && bookshelf.getStatus() == 0) {
            return;
        }
        bookshelfRepository.updateStatus(id, 0);
    }

    private void validateCreateRequest(CreateBookshelfRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getFloor() == null || req.getFloor() <= 0) {
            throw new IllegalArgumentException("floor 必须为正整数");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new IllegalArgumentException("code 不能为空");
        }
    }

    private BookshelfResponse toResponse(Bookshelf bookshelf) {
        BookshelfResponse resp = new BookshelfResponse();
        resp.setId(bookshelf.getId());
        resp.setFloor(bookshelf.getFloor());
        resp.setCode(bookshelf.getCode());
        resp.setStatus(bookshelf.getStatus());
        return resp;
    }
}
