package com.zx.bookstore.catalog.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.catalog.entity.BookCategory;
import com.zx.bookstore.catalog.mapper.BookCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookCategoryRepository {

    private final BookCategoryMapper mapper;

    public List<BookCategory> listEnabled() {
        return mapper.selectList(
                Wrappers.<BookCategory>lambdaQuery()
                        .eq(BookCategory::getStatus, 1)
                        .orderByAsc(BookCategory::getSort)
                        .orderByAsc(BookCategory::getId)
        );
    }

    public Optional<BookCategory> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<BookCategory> findEnabledById(Long id) {
        return findById(id).filter(c -> c.getStatus() != null && c.getStatus() == 1);
    }

    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Long count = mapper.selectCount(
                Wrappers.<BookCategory>lambdaQuery().eq(BookCategory::getName, name)
        );
        return count != null && count > 0;
    }

    public BookCategory save(BookCategory category) {
        LocalDateTime now = LocalDateTime.now();
        if (category.getId() == null) {
            if (category.getCreatedAt() == null) {
                category.setCreatedAt(now);
            }
            category.setUpdatedAt(now);
            mapper.insert(category);
            return category;
        }
        category.setUpdatedAt(now);
        mapper.updateById(category);
        return category;
    }
}
