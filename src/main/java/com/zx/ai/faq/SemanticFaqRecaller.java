package com.zx.ai.faq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * FAQ 语义召回：用 Milvus 向量检索「连续签到有什么奖励」「订单多久自动取消」等模糊问法。
 * <p>
 * 仅在 RAG 启用且 {@link VectorStore} Bean 存在时生效；否则不创建，
 * {@link com.zx.ai.tool.FaqTool} 通过 {@code ObjectProvider} 容忍其缺失并降级为关键词匹配。
 * <p>
 * 与 {@link com.zx.ai.recommend.SemanticBookRecaller} 共用同一 collection，
 * 通过 filter {@code type == 'faq'} 仅召回 FAQ 向量，避免混入图书向量。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
public class SemanticFaqRecaller {

    private static final String FILTER = "type == 'faq'";

    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double similarityThreshold;

    public SemanticFaqRecaller(
            VectorStore vectorStore,
            @Value("${ai.rag.top-k:8}") int defaultTopK,
            @Value("${ai.rag.similarity-threshold:0.6}") double similarityThreshold
    ) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 语义召回 FAQ 条目。
     *
     * @param query 自然语言查询，如「签到七天送什么」
     * @param topK  最多返回条数；null 用默认
     * @return 命中的 FAQ 条目（按相似度降序），无结果返回空
     */
    public List<FaqEntry> recall(String query, Integer topK) {
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
                log.info("semantic faq recall empty, query={}", query);
                return List.of();
            }
            List<FaqEntry> entries = new ArrayList<>(hits.size());
            for (Document doc : hits) {
                FaqEntry entry = toEntry(doc);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            log.info("semantic faq recall query={} hit={}", query, entries.size());
            return entries;
        } catch (Exception e) {
            log.warn("semantic faq recall failed, query={}", query, e);
            return List.of();
        }
    }

    private FaqEntry toEntry(Document doc) {
        Object topicObj = doc.getMetadata().get("topic");
        String topic = topicObj == null ? "" : topicObj.toString().trim();
        String text = doc.getText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        // 向量文本格式：{topic} | {answer} | 关键词:...
        // 这里取首段作为 answer 回填（去掉关键词增强段），保证 LLM 拿到完整答案。
        String answer = stripKeywordSegment(text, topic);
        return new FaqEntry(StringUtils.hasText(topic) ? topic : "FAQ", answer, List.of());
    }

    private String stripKeywordSegment(String text, String topic) {
        String body = text;
        if (StringUtils.hasText(topic) && body.startsWith(topic)) {
            body = body.substring(topic.length());
            if (body.startsWith(" | ")) {
                body = body.substring(3);
            }
        }
        int kwIdx = body.lastIndexOf(" | 关键词:");
        if (kwIdx > 0) {
            body = body.substring(0, kwIdx);
        }
        return body.trim();
    }
}
