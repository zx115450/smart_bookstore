package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.ReaderAiSummaryCache;
import com.zx.reader.mapper.ReaderAiSummaryCacheMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReaderAiSummaryCacheRepository {

    private final ReaderAiSummaryCacheMapper mapper;

    public Optional<ReaderAiSummaryCache> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<ReaderAiSummaryCache> findByCacheKey(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<ReaderAiSummaryCache>lambdaQuery()
                        .eq(ReaderAiSummaryCache::getCacheKey, cacheKey)
        ));
    }

    public ReaderAiSummaryCache save(ReaderAiSummaryCache cache) {
        if (cache.getId() == null) {
            if (cache.getCreatedAt() == null) {
                cache.setCreatedAt(LocalDateTime.now());
            }
            mapper.insert(cache);
            return cache;
        }
        mapper.updateById(cache);
        return cache;
    }
}
