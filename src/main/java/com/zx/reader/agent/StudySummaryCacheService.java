package com.zx.reader.agent;

import com.zx.reader.config.ReaderProperties;
import com.zx.reader.entity.ReaderAiSummaryCache;
import com.zx.reader.repository.ReaderAiSummaryCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 章节总结缓存：cache_key = ebookId:chapterNo:modelVersion。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudySummaryCacheService {

    private final ReaderAiSummaryCacheRepository cacheRepository;
    private final ReaderProperties readerProperties;

    public String cacheKey(Long ebookId, int chapterNo) {
        String model = readerProperties.getAgent().getModelVersion();
        if (!StringUtils.hasText(model)) {
            model = "default";
        }
        return ebookId + ":" + chapterNo + ":" + model.trim();
    }

    public Optional<String> get(Long ebookId, int chapterNo) {
        return cacheRepository.findByCacheKey(cacheKey(ebookId, chapterNo))
                .map(ReaderAiSummaryCache::getContent)
                .filter(StringUtils::hasText);
    }

    public void put(Long ebookId, int chapterNo, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        String key = cacheKey(ebookId, chapterNo);
        ReaderAiSummaryCache row = cacheRepository.findByCacheKey(key).orElseGet(ReaderAiSummaryCache::new);
        row.setCacheKey(key);
        row.setContent(content.trim());
        row.setModel(readerProperties.getAgent().getModelVersion());
        cacheRepository.save(row);
        log.info("study summary cache saved key={}", key);
    }
}
