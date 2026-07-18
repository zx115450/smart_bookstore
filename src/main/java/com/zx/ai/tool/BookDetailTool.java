package com.zx.ai.tool;

import com.zx.ai.support.ChatCardCollector;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.service.BookCatalogService;
import com.zx.bookstore.exception.BookstoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图书详情 Tool 适配层：按 bookId 查详情（含架位），内部调用 {@link BookCatalogService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookDetailTool {

    private final BookCatalogService bookCatalogService;

    @Tool(
            name = "getBookDetail",
            description = "根据图书 ID 查询单本上架图书详情，包含架位 shelfLocation、借阅库存等。已知 bookId 或需要确认某一本书的架位/库存时调用。"
    )
    public Map<String, Object> getBookDetail(
            @ToolParam(description = "图书 ID，来自 searchBooks 返回的 id") Long bookId
    ) {
        if (bookId == null || bookId <= 0) {
            return Map.of("found", false, "message", "bookId 无效");
        }
        try {
            BookResponse book = bookCatalogService.getBookDetail(bookId);
            Map<String, Object> view = BookToolViews.from(book);
            ChatCardCollector.offerBook(view);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", true);
            result.put("book", view);
            log.info("tool getBookDetail bookId={} title={}", bookId, book.getTitle());
            return result;
        } catch (BookstoreException e) {
            log.info("tool getBookDetail not found bookId={}", bookId);
            return Map.of("found", false, "message", e.getMessage() == null ? "图书不存在或已下架" : e.getMessage());
        }
    }
}
