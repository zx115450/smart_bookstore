package com.zx.bookstore.catalog.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.catalog.entity.Bookshelf;
import com.zx.bookstore.catalog.mapper.BookshelfMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BookshelfRepository {

    private final BookshelfMapper mapper;

    public Optional<Bookshelf> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<Bookshelf> findEnabledById(Long id) {
        return findById(id).filter(s -> s.getStatus() != null && s.getStatus() == 1);
    }

    public Map<Long, Bookshelf> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(
                Wrappers.<Bookshelf>lambdaQuery().in(Bookshelf::getId, ids)
        ).stream().collect(Collectors.toMap(Bookshelf::getId, s -> s));
    }

    public List<Bookshelf> listEnabled(Integer floor) {
        return mapper.selectList(
                Wrappers.<Bookshelf>lambdaQuery()
                        .eq(Bookshelf::getStatus, 1)
                        .eq(floor != null, Bookshelf::getFloor, floor)
                        .orderByAsc(Bookshelf::getFloor)
                        .orderByAsc(Bookshelf::getCode)
        );
    }

    public List<Bookshelf> pageAll(Integer floor, Integer status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        long offset = (safePage - 1) * safeSize;
        return mapper.selectList(
                Wrappers.<Bookshelf>lambdaQuery()
                        .eq(floor != null, Bookshelf::getFloor, floor)
                        .eq(status != null, Bookshelf::getStatus, status)
                        .orderByAsc(Bookshelf::getFloor)
                        .orderByAsc(Bookshelf::getCode)
                        .last("LIMIT " + safeSize + " OFFSET " + offset)
        );
    }

    public long countAll(Integer floor, Integer status) {
        Long count = mapper.selectCount(
                Wrappers.<Bookshelf>lambdaQuery()
                        .eq(floor != null, Bookshelf::getFloor, floor)
                        .eq(status != null, Bookshelf::getStatus, status)
        );
        return count == null ? 0 : count;
    }

    public boolean existsByFloorAndCode(Integer floor, String code) {
        if (floor == null || code == null || code.isBlank()) {
            return false;
        }
        Long count = mapper.selectCount(
                Wrappers.<Bookshelf>lambdaQuery()
                        .eq(Bookshelf::getFloor, floor)
                        .eq(Bookshelf::getCode, code.trim())
        );
        return count != null && count > 0;
    }

    public boolean existsByFloorAndCodeExceptId(Integer floor, String code, Long excludeId) {
        if (floor == null || code == null || code.isBlank()) {
            return false;
        }
        var wrapper = Wrappers.<Bookshelf>lambdaQuery()
                .eq(Bookshelf::getFloor, floor)
                .eq(Bookshelf::getCode, code.trim());
        if (excludeId != null) {
            wrapper.ne(Bookshelf::getId, excludeId);
        }
        Long count = mapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    public Bookshelf save(Bookshelf bookshelf) {
        LocalDateTime now = LocalDateTime.now();
        if (bookshelf.getId() == null) {
            if (bookshelf.getCreatedAt() == null) {
                bookshelf.setCreatedAt(now);
            }
            bookshelf.setUpdatedAt(now);
            mapper.insert(bookshelf);
            return bookshelf;
        }
        bookshelf.setUpdatedAt(now);
        mapper.updateById(bookshelf);
        return bookshelf;
    }

    public int updateStatus(Long id, int status) {
        Bookshelf update = new Bookshelf();
        update.setId(id);
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        return mapper.updateById(update);
    }
}
