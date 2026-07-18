package com.zx.ai.tool;

import com.zx.ai.support.ChatCardCollector;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.service.BookCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查书 Tool 适配层：暴露给 LLM，内部调用 {@link BookCatalogService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookSearchTool {

    private final BookCatalogService bookCatalogService;

    @Tool(
            name = "searchBooks",
            description = "按书名、作者或关键词检索本馆上架图书，返回书目列表（含架位 shelfLocation、借阅/售卖库存）。查有没有某本书、搜书时必须先调用本工具。"
    )
    public Map<String, Object> searchBooks(
            @ToolParam(description = "书名、作者或关键词，例如 Redis、Java 核心技术") String keyword,
            @ToolParam(required = false, description = "最多返回几本，默认 5，最大 10") Integer limit
    ) {
        String q = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(q)) {
            return Map.of(
                    "found", false,
                    "message", "请提供书名或关键词",
                    "books", List.of()
            );
        }
        int size = limit == null ? 5 : Math.min(Math.max(limit, 1), 10);
        PageResult<BookResponse> page = bookCatalogService.listBooks(null, q, 1, size);
        List<Map<String, Object>> books = page.getRecords().stream()
                .map(BookToolViews::from)
                .toList();
        ChatCardCollector.offerBooks(books);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", !books.isEmpty());
        result.put("total", page.getTotal());
        result.put("books", books);
        if (books.isEmpty()) {
            result.put("message", "未找到匹配的上架图书");
        }
        log.info("tool searchBooks keyword={} hit={}", q, books.size());
        return result;
    }
}
