package com.zx.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

/**
 * 启动预热：当 {@code ai.rag.enabled=true} 且 {@code ai.rag.prewarm-on-startup=true} 时，
 * 应用就绪后自动全量重建向量索引。量大时耗时，默认关闭，建议用管理端接口手动重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rag", name = "prewarm-on-startup", havingValue = "true")
@ConditionalOnBean(BookEmbeddingIndexer.class)
public class BookIndexPrewarmer implements ApplicationListener<ApplicationReadyEvent> {

    private final BookEmbeddingIndexer indexer;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            int total = indexer.reindexAll();
            log.info("prewarm vector index done on startup, total={}", total);
        } catch (Exception e) {
            log.warn("prewarm vector index failed on startup", e);
        }
    }
}
