package com.zx.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FaqTool 关键词召回路径的单元测试（不依赖真实向量库 / LLM）。
 * <p>
 * <b>为什么用纯 Mockito 而不是 Spring 容器？</b>
 * FaqTool 的关键词匹配逻辑是内存中的规则匹配，不需要 Spring 上下文。
 * 用 {@code mock(ObjectProvider.class)} 把依赖的语义召回器替换为 null，
 * 即可让被测对象走“纯关键词”分支，避免加载 Milvus / Redis 等中间件。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link ObjectProvider}：Spring 中用来容忍 Bean 缺失；{@code getIfAvailable()} 返回 null
 *       时，FaqTool 自动降级到关键词规则。</li>
 *   <li>这里用 {@code @SuppressWarnings} 压制泛型警告，因为测试里直接 mock 的是 raw type。</li>
 * </ul>
 */
class FaqToolKeywordTest {

    private final ObjectProvider<?> emptyProvider = mock(ObjectProvider.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    private FaqTool buildTool() {
        when(((ObjectProvider) emptyProvider).getIfAvailable()).thenReturn(null);
        return new FaqTool((ObjectProvider) emptyProvider);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldHitBorrowRuleByKeyword() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("怎么借书", 3);

        assertTrue((Boolean) result.get("found"), result.toString());
        assertEquals("keyword", result.get("source"));
        assertTrue(result.containsKey("faqs"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldHitCheckinRuleByKeyword() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("连续签到有什么奖励", 3);

        assertTrue((Boolean) result.get("found"), result.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldHitEbookPreviewRuleByKeyword() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("第三章打不开是不是试看限制", 3);

        assertTrue((Boolean) result.get("found"), result.toString());
        assertEquals("keyword", result.get("source"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldHitOverdueDowngradeByKeyword() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("逾期了电子书会停权吗", 3);

        assertTrue((Boolean) result.get("found"), result.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldReturnNotFoundForUnrelatedQuery() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("今天天气怎么样", 3);

        assertFalse((Boolean) result.get("found"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldRejectEmptyQuery() {
        FaqTool tool = buildTool();

        Map<String, Object> result = tool.searchFaq("  ", 3);

        assertFalse((Boolean) result.get("found"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldCapLimitBetween1And5() {
        FaqTool tool = buildTool();

        Map<String, Object> tooSmall = tool.searchFaq("借书", 0);
        Map<String, Object> tooLarge = tool.searchFaq("借书", 10);
        Map<String, Object> valid = tool.searchFaq("借书", 2);

        assertTrue((Integer) tooSmall.get("total") <= 5);
        assertTrue((Integer) tooLarge.get("total") <= 5);
        assertTrue((Integer) valid.get("total") <= 2);
    }
}
