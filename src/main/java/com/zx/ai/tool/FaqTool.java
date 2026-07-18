package com.zx.ai.tool;

import com.zx.ai.faq.FaqEntry;
import com.zx.ai.faq.FaqKnowledgeBase;
import com.zx.ai.faq.SemanticFaqRecaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FAQ Tool 适配层：回答借阅流程、签到送券、预约规则、购书用券等业务规则。
 * <p>
 * 检索策略（G.4 升级）：
 * <ol>
 *   <li>RAG 启用时优先走 {@link SemanticFaqRecaller} 语义检索（Milvus 向量），命中模糊问法；</li>
 *   <li>语义无结果或 RAG 关闭时降级为关键词打分匹配（D 板块能力，保留兜底）；</li>
 *   <li>两条路径都不命中则返回 notFound 引导。</li>
 * </ol>
 * 不查 MySQL 业务表，仅返回固定业务规则文案，由 LLM 组织最终回复。
 */
@Slf4j
@Component
public class FaqTool {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 5;

    private final List<FaqEntry> knowledgeBase = FaqKnowledgeBase.entries();
    /** RAG 关闭时为空，降级为关键词匹配。 */
    private final ObjectProvider<SemanticFaqRecaller> semanticFaqRecallerProvider;

    public FaqTool(ObjectProvider<SemanticFaqRecaller> semanticFaqRecallerProvider) {
        this.semanticFaqRecallerProvider = semanticFaqRecallerProvider;
    }

    @Tool(
            name = "searchFaq",
            description = "查询智慧书城的业务规则与流程说明，例如：怎么借书/还书、待取书在哪看架位、借阅到期/逾期规则、连续签到奖励、怎么预约自习室、购书下单与自动取消、优惠券怎么用。涉及规则性、流程性问题必须调用本工具，禁止编造规则细节。不要用于查具体某本书的库存或架位（那应该用 searchBooks / getBookDetail）。"
    )
    public Map<String, Object> searchFaq(
            @ToolParam(description = "用户问题或关键词，例如：怎么借书、签到奖励、怎么预约座位、订单多久取消") String query,
            @ToolParam(required = false, description = "最多返回几条，默认 3，最大 5") Integer limit
    ) {
        String q = query == null ? "" : query.trim();
        if (!StringUtils.hasText(q)) {
            return notFound("请描述你想了解的业务规则，例如「怎么借书」「签到奖励」");
        }
        int topN = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        // G.4：优先语义召回
        SemanticFaqRecaller semantic = semanticFaqRecallerProvider.getIfAvailable();
        if (semantic != null) {
            List<FaqEntry> semanticHits = semantic.recall(q, topN);
            if (!semanticHits.isEmpty()) {
                return buildHitResult(q, semanticHits, topN, "semantic");
            }
            log.info("tool searchFaq semantic empty, fallback to keyword, query={}", q);
        }

        // 兜底：关键词打分匹配
        return keywordSearch(q, topN);
    }

    private Map<String, Object> keywordSearch(String q, int topN) {
        List<String> queryTokens = tokenize(q);
        List<ScoredEntry> scored = new ArrayList<>();
        for (int i = 0; i < knowledgeBase.size(); i++) {
            FaqEntry entry = knowledgeBase.get(i);
            int score = scoreEntry(entry, q, queryTokens);
            if (score > 0) {
                scored.add(new ScoredEntry(i, entry, score));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        if (scored.isEmpty()) {
            log.info("tool searchFaq query={} no hit", q);
            return notFound("暂无匹配的业务规则，建议换种问法或联系人工客服");
        }

        List<FaqEntry> hits = new ArrayList<>(Math.min(topN, scored.size()));
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            hits.add(scored.get(i).entry);
        }
        return buildHitResult(q, hits, topN, "keyword");
    }

    private static Map<String, Object> buildHitResult(String q, List<FaqEntry> hits, int topN, String source) {
        List<FaqEntry> trimmed = hits.size() > topN ? hits.subList(0, topN) : hits;
        List<Map<String, Object>> faqs = new ArrayList<>(trimmed.size());
        for (FaqEntry entry : trimmed) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("topic", entry.topic());
            hit.put("answer", entry.answer());
            faqs.add(hit);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("total", faqs.size());
        result.put("source", source);
        result.put("faqs", faqs);
        log.info("tool searchFaq query={} source={} hit={}", q, source, faqs.size());
        return result;
    }

    private static Map<String, Object> notFound(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("total", 0);
        result.put("faqs", List.of());
        result.put("message", message);
        return result;
    }

    private int scoreEntry(FaqEntry entry, String rawQuery, List<String> queryTokens) {
        if (entry.keywords() == null || entry.keywords().isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String kw : entry.keywords()) {
            if (rawQuery.contains(kw)) {
                score += 3;
                continue;
            }
            for (String token : queryTokens) {
                if (kw.contains(token) || token.contains(kw)) {
                    score += 1;
                    break;
                }
            }
        }
        return score;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String t : text.split("[\\s,，。？?！!、；;:：]+")) {
            String s = t.trim();
            if (s.length() >= 2) {
                tokens.add(s);
            }
        }
        return tokens;
    }

    private record ScoredEntry(int index, FaqEntry entry, int score) {
    }
}
