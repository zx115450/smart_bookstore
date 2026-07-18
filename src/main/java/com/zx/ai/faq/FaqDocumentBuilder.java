package com.zx.ai.faq;

import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@link FaqEntry} 压成给 Embedding 用的文本 + metadata。
 * <p>
 * 文本：{@code {topic} | {answer}}，关键词作为附加增强拼到末尾，提升语义召回命中率。
 * metadata：{@code type=faq}（与图书向量 {@code type=book} 在同一 collection 内分区过滤）、
 * {@code topic}（便于回查/展示）。文档 id = {@code faq:{index}}，便于删旧向量。
 * <p>
 * 设计说明：复用 {@code bookstore_book} collection，按 metadata {@code type} 分区，
 * 避免为 FAQ 单独建第二个 Milvus collection / 第二个 VectorStore Bean（Spring AI Milvus
 * 自动装配只绑定单一 collection）。语义检索时通过 filter {@code type == 'faq'} 隔离。
 */
public final class FaqDocumentBuilder {

    /** 向量库文档 id 前缀，便于按索引删旧向量。 */
    public static final String DOC_ID_PREFIX = "faq:";

    /** metadata 中标记 FAQ 类型，供 Milvus filter 表达式分区过滤。 */
    public static final String META_TYPE_VALUE = "faq";

    private FaqDocumentBuilder() {
    }

    /**
     * 构建单条 FAQ 向量文档。
     *
     * @param index 在知识库中的稳定下标，用作文档 id 后缀
     */
    public static Document build(FaqEntry entry, int index) {
        return Document.builder()
                .id(DOC_ID_PREFIX + index)
                .text(buildText(entry))
                .metadata(buildMetadata(entry))
                .build();
    }

    static String buildText(FaqEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(entry.topic()));
        sb.append(" | ").append(nullToEmpty(entry.answer()));
        if (entry.keywords() != null && !entry.keywords().isEmpty()) {
            sb.append(" | 关键词:").append(String.join(" ", entry.keywords()));
        }
        return sb.toString();
    }

    static Map<String, Object> buildMetadata(FaqEntry entry) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", META_TYPE_VALUE);
        if (StringUtils.hasText(entry.topic())) {
            meta.put("topic", entry.topic());
        }
        return meta;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
