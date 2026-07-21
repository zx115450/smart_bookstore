/**
 * 图书推荐：多路召回（热度 / 分类 / 语义 / 个性化）→ 回查业务库 → 精排 → 理由生成。
 * 入口为 {@link com.zx.ai.recommend.BookRecommendService}，对外经 {@link com.zx.ai.tool.BookRecommendTool} 暴露给 LLM。
 */
package com.zx.ai.recommend;
