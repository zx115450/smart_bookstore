package com.zx.ai.support;

import com.zx.ai.dto.ChatCard;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收集本轮对话中查书 Tool 产出的图书卡片。
 * <p>
 * Spring AI 自动 Tool 循环结束后，最终 {@code ChatResponse} 只有模型文案，
 * 不含 Tool 原始 JSON，因此由 Tool 侧写入、Service 侧 drain。
 */
public final class ChatCardCollector {

    private static final ThreadLocal<List<ChatCard>> HOLDER = ThreadLocal.withInitial(ArrayList::new);

    private ChatCardCollector() {
    }

    /** 从 BookToolViews 风格的 Map 追加一张 type=book 卡片。 */
    public static void offerBook(Map<String, Object> bookView) {
        ChatCard card = toBookCard(bookView);
        if (card != null) {
            HOLDER.get().add(card);
        }
    }

    public static void offerBooks(List<Map<String, Object>> bookViews) {
        if (bookViews == null || bookViews.isEmpty()) {
            return;
        }
        for (Map<String, Object> view : bookViews) {
            offerBook(view);
        }
    }

    /** 直接追加一张已构建的卡片（推荐卡片等）。 */
    public static void offer(ChatCard card) {
        if (card != null) {
            HOLDER.get().add(card);
        }
    }

    /** 批量追加推荐卡片。 */
    public static void offerRecommends(List<ChatCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return;
        }
        for (ChatCard card : cards) {
            offer(card);
        }
    }

    /**
     * 取出本线程收集的卡片（按 bookId 去重，保留首次），并清空。
     */
    public static List<ChatCard> drain() {
        List<ChatCard> raw = HOLDER.get();
        HOLDER.remove();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<Long, ChatCard> dedup = new LinkedHashMap<>();
        List<ChatCard> noId = new ArrayList<>();
        for (ChatCard card : raw) {
            if (card.getBookId() == null) {
                noId.add(card);
            } else {
                dedup.putIfAbsent(card.getBookId(), card);
            }
        }
        List<ChatCard> result = new ArrayList<>(dedup.values());
        result.addAll(noId);
        return List.copyOf(result);
    }

    public static void clear() {
        HOLDER.remove();
    }

    static ChatCard toBookCard(Map<String, Object> bookView) {
        if (bookView == null || bookView.isEmpty()) {
            return null;
        }
        Long bookId = toLong(bookView.get("id"));
        String title = toString(bookView.get("title"));
        if (bookId == null && !StringUtils.hasText(title)) {
            return null;
        }
        return ChatCard.book(
                bookId,
                title,
                toString(bookView.get("author")),
                toString(bookView.get("shelfLocation")),
                toInteger(bookView.get("borrowStock")),
                toInteger(bookView.get("saleStock"))
        );
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
