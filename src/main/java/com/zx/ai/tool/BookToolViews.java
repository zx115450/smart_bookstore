package com.zx.ai.tool;

import com.zx.bookstore.catalog.dto.BookResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/** 把书目响应压成给大模型看的瘦字段。 */
final class BookToolViews {

    private BookToolViews() {
    }

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
