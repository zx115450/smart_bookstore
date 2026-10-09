package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.mapper.EbookBookMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EbookBookRepository {

    private final EbookBookMapper mapper;

    public Optional<EbookBook> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<EbookBook> findBySourceFileId(String sourceFileId) {
        if (sourceFileId == null || sourceFileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<EbookBook>lambdaQuery()
                        .eq(EbookBook::getSourceFileId, sourceFileId)
                        .last("LIMIT 1")
        ));
    }

    /** 实体书绑定的上架线上书（status=1）；一本实体书最多取一条。 */
    public Optional<EbookBook> findEnabledByBookId(Long bookId) {
        if (bookId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<EbookBook>lambdaQuery()
                        .eq(EbookBook::getBookId, bookId)
                        .eq(EbookBook::getStatus, 1)
                        .orderByDesc(EbookBook::getId)
                        .last("LIMIT 1")
        ));
    }

    /**
     * 批量查实体书 → 上架线上书 ID；同一 bookId 多条时取 id 最大者。
     */
    public Map<Long, Long> findEnabledEbookIdByBookIds(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EbookBook> rows = mapper.selectList(
                Wrappers.<EbookBook>lambdaQuery()
                        .in(EbookBook::getBookId, bookIds)
                        .eq(EbookBook::getStatus, 1)
                        .orderByDesc(EbookBook::getId)
        );
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (EbookBook row : rows) {
            if (row.getBookId() == null || row.getId() == null) {
                continue;
            }
            map.putIfAbsent(row.getBookId(), row.getId());
        }
        return map;
    }

    public EbookBook save(EbookBook book) {
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
}
