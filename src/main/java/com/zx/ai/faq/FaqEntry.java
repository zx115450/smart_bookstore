package com.zx.ai.faq;

import java.util.Arrays;
import java.util.List;

/**
 * FAQ 知识条目：主题 + 答案 + 命中关键词。
 * <p>
 * 由 {@link FaqKnowledgeBase} 集中构建，供 {@link com.zx.ai.tool.FaqTool} 关键词匹配、
 * {@link FaqEmbeddingIndexer} 离线索引、{@link SemanticFaqRecaller} 在线语义检索共用。
 *
 * @param topic    主题标题
 * @param answer   答案正文
 * @param keywords 命中关键词（用于关键词打分与索引文本增强）
 */
public record FaqEntry(String topic, String answer, List<String> keywords) {

    public static FaqEntry of(String topic, String answer, String... keywords) {
        return new FaqEntry(topic, answer, Arrays.asList(keywords));
    }
}
