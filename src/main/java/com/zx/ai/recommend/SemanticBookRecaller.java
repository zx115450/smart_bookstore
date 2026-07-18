package com.zx.ai.recommend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义召回：用 Milvus 向量检索「想学 Redis」「入门 Spring」等模糊查询。
 * <p>
 * 仅在 RAG 启用且 {@link VectorStore} Bean 存在时生效；否则不创建，
 * {@link BookRecommendService} 通过 {@code ObjectProvider} 容忍其缺失。
 * 召回后仅返回 bookId，由上层回查 MySQL 补实时架位/库存。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
public class SemanticBookRecaller {

    private static final String FILTER = "type == 'book' && status == 1";

    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double similarityThreshold;

    @Autowired
    public SemanticBookRecaller(
            VectorStore vectorStore,
            @Value("${ai.rag.top-k:8}") int defaultTopK,
            @Value("${ai.rag.similarity-threshold:0.6}") double similarityThreshold
    ) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 语义召回 bookId 列表（已过滤下架书）。
     *
     * @param query 自然语言查询，如「想学 Redis 缓存」
     * @param topK  最多返回条数；null 用默认
     * @return bookId 列表（按相似度降序），无结果返回空
     */
    public List<Long> recall(String query, Integer topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int k = topK == null ? defaultTopK : Math.min(Math.max(topK, 1), 20);
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query.trim())
                    .topK(k)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(FILTER)
                    .build();
            List<Document> hits = vectorStore.similaritySearch(request);
            if (hits == null || hits.isEmpty()) {
                log.info("semantic recall empty, query={}", query);
                return List.of();
            }
            List<Long> ids = new ArrayList<>(hits.size());
            for (Document doc : hits) {
                Object bookId = doc.getMetadata().get("bookId");
                if (bookId instanceof Number n) {
                    ids.add(n.longValue());
                } else if (bookId != null) {
                    try {
                        ids.add(Long.parseLong(bookId.toString().trim()));
                    } catch (NumberFormatException ignore) {
                        // skip
                    }
                }
            }
            log.info("semantic recall query={} hit={}", query, ids.size());
            return ids;
        } catch (Exception e) {
            log.warn("semantic recall failed, query={}", query, e);
            return List.of();
        }
    }
}
