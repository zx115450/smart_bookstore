package com.zx.ai.controller;

import com.zx.ai.faq.FaqEmbeddingIndexer;
import com.zx.ai.rag.BookEmbeddingIndexer;
import com.zx.auth.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 管理端：向量索引重建。仅 RAG 启用时 {@link BookEmbeddingIndexer} / {@link FaqEmbeddingIndexer} 存在。
 */
@RestController
@RequestMapping("/api/ai/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAdminController {

    private final ObjectProvider<BookEmbeddingIndexer> indexerProvider;
    private final ObjectProvider<FaqEmbeddingIndexer> faqIndexerProvider;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reindex")
    public ApiResponse<Map<String, Object>> reindexAll() {
        BookEmbeddingIndexer indexer = indexerProvider.getIfAvailable();
        if (indexer == null) {
            return ApiResponse.ok(Map.of("enabled", false, "message", "RAG 未启用（ai.rag.enabled=false），无需重建索引"));
        }
        int total = indexer.reindexAll();
        return ApiResponse.ok(Map.of("enabled", true, "indexed", total));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reindex/{bookId}")
    public ApiResponse<Map<String, Object>> reindexBook(@PathVariable Long bookId) {
        BookEmbeddingIndexer indexer = indexerProvider.getIfAvailable();
        if (indexer == null) {
            return ApiResponse.ok(Map.of("enabled", false, "message", "RAG 未启用"));
        }
        indexer.reindexBook(bookId);
        return ApiResponse.ok(Map.of("enabled", true, "bookId", bookId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reindex-faq")
    public ApiResponse<Map<String, Object>> reindexFaq() {
        FaqEmbeddingIndexer indexer = faqIndexerProvider.getIfAvailable();
        if (indexer == null) {
            return ApiResponse.ok(Map.of("enabled", false, "message", "RAG 未启用（ai.rag.enabled=false），无需重建 FAQ 索引"));
        }
        int total = indexer.reindexAll();
        return ApiResponse.ok(Map.of("enabled", true, "indexed", total));
    }
}
