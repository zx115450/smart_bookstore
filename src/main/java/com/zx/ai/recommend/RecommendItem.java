package com.zx.ai.recommend;

import com.zx.bookstore.catalog.dto.BookResponse;

/**
 * 推荐结果项。
 *
 * @param book   上架图书详情（含 shelfLocation / 库存）
 * @param heat   热度分（0 表示无热度数据）
 * @param reason 推荐理由（中文，供 LLM 与卡片展示）
 */
public record RecommendItem(BookResponse book, int heat, String reason) {
}
