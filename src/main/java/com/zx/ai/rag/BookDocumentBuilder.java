package com.zx.ai.rag;

import com.zx.bookstore.catalog.dto.BookResponse;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把书目压成给 Embedding 用的摘要级文本 + metadata。
 * <p>
 * 文本：{@code {title} | {author} | 分类:{categoryName} | {description}}，
 * 不喂全书；description 截断到 {@link #MAX_DESC_LEN} 字符以内。
 * metadata：bookId / categoryId / type=book / status，供 Milvus 过滤与回查。
 */
public final class BookDocumentBuilder {

    /** 摘要级 description 截断长度，避免超出 Embedding token 上限。 */
    static final int MAX_DESC_LEN = 500;

    /** 向量库文档 id 前缀，便于按 bookId 删旧向量。 */
    public static final String DOC_ID_PREFIX = "book:";

    private BookDocumentBuilder() {
    }

    public static Document build(BookResponse book) {
        String text = buildText(book);
        Map<String, Object> metadata = buildMetadata(book);
        return Document.builder()
                .id(DOC_ID_PREFIX + book.getId())
                .text(text)
                .metadata(metadata)
                .build();
    }

    static String buildText(BookResponse book) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(book.getTitle()));
        sb.append(" | ").append(nullToEmpty(book.getAuthor()));
        if (StringUtils.hasText(book.getCategoryName())) {
            sb.append(" | 分类:").append(book.getCategoryName());
        }
        if (StringUtils.hasText(book.getDescription())) {
            sb.append(" | ").append(truncate(book.getDescription(), MAX_DESC_LEN));
        }
        return sb.toString();
    }

    static Map<String, Object> buildMetadata(BookResponse book) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("bookId", book.getId());
        if (book.getCategoryId() != null) {
            meta.put("categoryId", book.getCategoryId());
        }
        meta.put("type", "book");
        if (book.getStatus() != null) {
            meta.put("status", book.getStatus());
        }
        return meta;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String text, int max) {
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...";
    }
}
