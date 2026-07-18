package com.zx.ai.faq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FAQ 离线索引：把 {@link FaqKnowledgeBase} 中的规则条目写入 Milvus。仅在 RAG 启用时生效。
 * <p>
 * 与 {@code BookEmbeddingIndexer} 共用同一个 {@link VectorStore}（同一 collection
 * {@code bookstore_book}），通过 metadata {@code type=faq} 分区，避免引入第二个 VectorStore Bean。
 * <p>
 * <ul>
 *   <li>{@link #reindexAll()} 全量重建：先按 {@code faq:*} 前缀删除旧 FAQ 向量，再批量写入。</li>
 * </ul>
 * 向量文档 id = {@code faq:{index}}（index 为知识库下标，稳定不变）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
public class FaqEmbeddingIndexer {

    private static final int ADD_BATCH = 16;

    private final VectorStore vectorStore;

    public FaqEmbeddingIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 全量重建 FAQ 索引：返回写入条数。
     * <p>
     * 先尝试删除已有 {@code faq:*} 文档（VectorStore 仅支持按确切 id 删除，故按已知下标遍历删除），
     * 再批量 embed + 写入。FAQ 条目数量有限（个位数），全量重建开销可忽略。
     */
    public int reindexAll() {
        log.info("reindex all faq start");
        List<FaqEntry> entries = FaqKnowledgeBase.entries();
        deleteOldFaqVectors(entries.size());
        int total = 0;
        List<Document> batch = new ArrayList<>(ADD_BATCH);
        for (int i = 0; i < entries.size(); i++) {
            batch.add(FaqDocumentBuilder.build(entries.get(i), i));
            if (batch.size() >= ADD_BATCH) {
                total += addBatch(batch);
                batch = new ArrayList<>(ADD_BATCH);
            }
        }
        if (!batch.isEmpty()) {
            total += addBatch(batch);
        }
        log.info("reindex all faq done, total={}", total);
        return total;
    }

    private void deleteOldFaqVectors(int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(FaqDocumentBuilder.DOC_ID_PREFIX + i);
        }
        try {
            vectorStore.delete(ids);
        } catch (Exception e) {
            log.warn("delete old faq vectors failed, count={}", count, e);
        }
    }

    private int addBatch(List<Document> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            vectorStore.add(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("vectorStore.add faq failed, size={}", batch.size(), e);
            return 0;
        }
    }
}
