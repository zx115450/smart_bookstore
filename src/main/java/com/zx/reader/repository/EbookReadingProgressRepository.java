package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.EbookReadingProgress;
import com.zx.reader.mapper.EbookReadingProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EbookReadingProgressRepository {

    private final EbookReadingProgressMapper mapper;

    public Optional<EbookReadingProgress> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<EbookReadingProgress> findByUserAndEbook(Long userId, Long ebookId) {
        if (userId == null || ebookId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<EbookReadingProgress>lambdaQuery()
                        .eq(EbookReadingProgress::getUserId, userId)
                        .eq(EbookReadingProgress::getEbookId, ebookId)
        ));
    }

    public EbookReadingProgress save(EbookReadingProgress progress) {
        LocalDateTime now = LocalDateTime.now();
        progress.setUpdatedAt(now);
        if (progress.getId() == null) {
            mapper.insert(progress);
            return progress;
        }
        mapper.updateById(progress);
        return progress;
    }
}
