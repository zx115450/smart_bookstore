package com.zx.ai.tool;

import com.zx.ai.dto.ChatCard;
import com.zx.ai.recommend.BookRecommendService;
import com.zx.ai.recommend.RecommendItem;
import com.zx.ai.support.AiUserContext;
import com.zx.ai.support.ChatCardCollector;
import com.zx.bookstore.catalog.dto.BookResponse;
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
 * 推荐 Tool 适配层：基于规则（热度 + 分类 + 关键词）召回，不依赖向量库。
 * <p>
 * 支持 intent：EXPLORE / LEARN / SIMILAR / RELAX / PERSONALIZED。
 * PERSONALIZED 需登录，由 {@link com.zx.ai.recommend.BookRecommendService} 通过
 * {@link com.zx.ai.support.AiUserContext} 取 userId 构建画像。
 * SIMILAR 可传 seedBookId 基于种子书找同类/语义相似。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookRecommendTool {

    private static final List<String> SUPPORTED_INTENTS =
            List.of("EXPLORE", "LEARN", "SIMILAR", "RELAX", "PERSONALIZED");

    private final BookRecommendService bookRecommendService;

    @Tool(
            name = "recommendBooks",
            description = "向用户推荐图书，用于回答「有什么书推荐」「想学 XX 推荐几本」「文学区有什么」「推荐一本小说」「根据我借过的推荐」「和这本书类似的」等推荐类问题。intent 取值：EXPLORE(随便看看/有什么书)、LEARN(想学某主题)、SIMILAR(找类似的，可传 seedBookId 基于某本书找相似)、RELAX(休闲读物)、PERSONALIZED(根据当前登录用户的借阅/购书历史个性化推荐，需登录)。可选 query 关键词、categoryId 分类、seedBookId 种子书、limit 数量(默认3最大5)。推荐结果必须来自本工具，禁止编造书名。不要用本工具查具体某本书的库存(用 searchBooks)。"
    )
    public Map<String, Object> recommendBooks(
            @ToolParam(description = "推荐意图：EXPLORE/LEARN/SIMILAR/RELAX/PERSONALIZED") String intent,
            @ToolParam(required = false, description = "关键词，如 Redis、Java、小说") String query,
            @ToolParam(required = false, description = "分类 ID，指定则在分类内推荐") Long categoryId,
            @ToolParam(required = false, description = "种子书 ID，SIMILAR 时基于此书找同类/相似") Long seedBookId,
            @ToolParam(required = false, description = "返回数量，默认 3，最大 5") Integer limit
    ) {
        String safeIntent = normalizeIntent(intent);
        List<RecommendItem> items = bookRecommendService.recommend(safeIntent, query, categoryId, seedBookId, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        if (items.isEmpty()) {
            String message = "PERSONALIZED".equals(safeIntent) && !AiUserContext.isLoggedIn()
                    ? "个性化推荐需登录，请引导用户先登录后再试"
                    : "暂无合适的推荐图书，可建议用户换个主题或浏览 /api/books";
            result.put("found", false);
            result.put("total", 0);
            result.put("books", List.of());
            result.put("message", message);
            log.info("tool recommendBooks intent={} query={} categoryId={} seed={} empty",
                    safeIntent, query, categoryId, seedBookId);
            return result;
        }

        List<Map<String, Object>> books = items.stream()
                .map(this::toView)
                .toList();
        List<ChatCard> cards = items.stream()
                .map(this::toCard)
                .toList();
        ChatCardCollector.offerRecommends(cards);

        result.put("found", true);
        result.put("total", books.size());
        result.put("intent", safeIntent);
        result.put("books", books);
        log.info("tool recommendBooks intent={} query={} categoryId={} seed={} hit={}",
                safeIntent, query, categoryId, seedBookId, books.size());
        return result;
    }

    private String normalizeIntent(String intent) {
        String s = StringUtils.hasText(intent) ? intent.trim().toUpperCase() : "EXPLORE";
        return SUPPORTED_INTENTS.contains(s) ? s : "EXPLORE";
    }

    private Map<String, Object> toView(RecommendItem item) {
        BookResponse b = item.book();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", b.getId());
        view.put("title", b.getTitle());
        view.put("author", b.getAuthor());
        view.put("categoryName", b.getCategoryName());
        view.put("borrowStock", b.getBorrowStock());
        view.put("shelfLocation", b.getShelfLocation());
        view.put("heat", item.heat());
        view.put("recommendReason", item.reason());
        return view;
    }

    private ChatCard toCard(RecommendItem item) {
        BookResponse b = item.book();
        return ChatCard.recommend(
                b.getId(),
                b.getTitle(),
                b.getAuthor(),
                b.getShelfLocation(),
                b.getBorrowStock(),
                item.reason()
        );
    }
}
