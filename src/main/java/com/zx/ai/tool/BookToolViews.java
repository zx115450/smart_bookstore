package com.zx.ai.tool;

import com.zx.bookstore.catalog.dto.BookResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@link BookResponse} 压成给大模型 / 卡片用的瘦字段 Map。
 * 截断 description，避免 Tool 返回过长占用上下文。
 */
final class BookToolViews {

    private BookToolViews() {
    }

    /** 统一字段名：id / title / author / shelfLocation / borrowStock / saleStock 等。 */
    static Map<String, Object> from(BookResponse book) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", book.getId());
        view.put("title", book.getTitle());
        view.put("author", book.getAuthor());
        view.put("categoryName", book.getCategoryName());
        view.put("borrowStock", book.getBorrowStock());
        view.put("saleStock", book.getSaleStock());
        view.put("shelfLocation", book.getShelfLocation());
        view.put("status", book.getStatus());
        view.put("description", truncate(book.getDescription(), 200));
        return view;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...";
    }
}
