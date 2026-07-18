package com.zx.ai.recommend;

import java.util.Map;

/**
 * 图书热度统计（推荐召回用）。
 *
 * @param bookId 图书 ID
 * @param heat   热度分（借阅单数 + 已支付销量，加权合并）
 */
public record HotBookStat(Long bookId, int heat) {

    public static HotBookStat from(Map<String, Object> row) {
        Long bookId = toLong(row.get("bookId"));
        int heat = toInt(row.get("heat"));
        return new HotBookStat(bookId, heat);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (NumberFormatException e) { return 0; }
    }
}
