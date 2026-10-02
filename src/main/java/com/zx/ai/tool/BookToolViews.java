package com.zx.ai.tool;

import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.reader.entity.EbookBook;

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
        return from(book, null);
    }

    /**
     * @param ebook 绑定的线上书；null 表示无电子书（{@code hasEbook=false}）
     */
    static Map<String, Object> from(BookResponse book, EbookBook ebook) {
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
        if (ebook != null && ebook.getId() != null) {
            view.put("hasEbook", true);
            view.put("ebookId", ebook.getId());
            view.put("previewChapters", ebook.getPreviewChapters() == null ? 0 : ebook.getPreviewChapters());
            view.put("totalChapters", ebook.getTotalChapters() == null ? 0 : ebook.getTotalChapters());
            view.put("ebookFormat", ebook.getFormat());
        } else {
            view.put("hasEbook", false);
            view.put("ebookId", null);
        }
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
