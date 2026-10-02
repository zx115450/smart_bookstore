package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.UserNote;
import com.zx.reader.mapper.UserNoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserNoteRepository {

    private final UserNoteMapper mapper;

    public Optional<UserNote> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<UserNote> listByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return mapper.selectList(
                Wrappers.<UserNote>lambdaQuery()
                        .eq(UserNote::getUserId, userId)
                        .orderByDesc(UserNote::getId)
        );
    }

    public List<UserNote> listByUserAndEbook(Long userId, Long ebookId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        var wrapper = Wrappers.<UserNote>lambdaQuery()
                .eq(UserNote::getUserId, userId)
                .orderByDesc(UserNote::getId);
        if (ebookId != null) {
            wrapper.eq(UserNote::getEbookId, ebookId);
        }
        return mapper.selectList(wrapper);
    }

    public List<UserNote> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(
                Wrappers.<UserNote>lambdaQuery()
                        .in(UserNote::getId, ids)
                        .orderByAsc(UserNote::getId)
        );
    }

    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        return mapper.deleteById(id) > 0;
    }

    public UserNote save(UserNote note) {
        LocalDateTime now = LocalDateTime.now();
        if (note.getId() == null) {
            if (note.getCreatedAt() == null) {
                note.setCreatedAt(now);
            }
            note.setUpdatedAt(now);
            mapper.insert(note);
            return note;
        }
        note.setUpdatedAt(now);
        mapper.updateById(note);
        return note;
    }
}
