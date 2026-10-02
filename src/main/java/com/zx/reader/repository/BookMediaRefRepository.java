package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.BookMediaRef;
import com.zx.reader.mapper.BookMediaRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookMediaRefRepository {

    private final BookMediaRefMapper mapper;

    public Optional<BookMediaRef> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<BookMediaRef> listByBookId(Long bookId) {
        if (bookId == null) {
            return Collections.emptyList();
        }
        return mapper.selectList(
                Wrappers.<BookMediaRef>lambdaQuery()
                        .eq(BookMediaRef::getBookId, bookId)
                        .eq(BookMediaRef::getStatus, 1)
                        .orderByAsc(BookMediaRef::getSortOrder)
                        .orderByAsc(BookMediaRef::getId)
        );
    }

    public Optional<BookMediaRef> findActiveByBookIdAndId(Long bookId, Long refId) {
        if (bookId == null || refId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<BookMediaRef>lambdaQuery()
                        .eq(BookMediaRef::getBookId, bookId)
                        .eq(BookMediaRef::getId, refId)
                        .eq(BookMediaRef::getStatus, 1)
        ));
    }

    public Optional<BookMediaRef> findByBookIdAndFileId(Long bookId, String fileId) {
        if (bookId == null || fileId == null || fileId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<BookMediaRef>lambdaQuery()
                        .eq(BookMediaRef::getBookId, bookId)
                        .eq(BookMediaRef::getFileId, fileId)
        ));
    }

    public BookMediaRef save(BookMediaRef ref) {
        LocalDateTime now = LocalDateTime.now();
        if (ref.getId() == null) {
            if (ref.getCreatedAt() == null) {
                ref.setCreatedAt(now);
            }
            ref.setUpdatedAt(now);
            mapper.insert(ref);
            return ref;
        }
        ref.setUpdatedAt(now);
        mapper.updateById(ref);
        return ref;
    }

    /** 软删除：status=0。 */
    public boolean softDelete(Long bookId, Long refId) {
        if (bookId == null || refId == null) {
            return false;
        }
        BookMediaRef update = new BookMediaRef();
        update.setStatus(0);
        update.setUpdatedAt(LocalDateTime.now());
        return mapper.update(
                update,
                Wrappers.<BookMediaRef>lambdaQuery()
                        .eq(BookMediaRef::getBookId, bookId)
                        .eq(BookMediaRef::getId, refId)
                        .eq(BookMediaRef::getStatus, 1)
        ) > 0;
    }
}
