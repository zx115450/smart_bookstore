package com.zx.ai.faq;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FaqDocumentBuilder 的纯单元测试：验证 FAQ 文档的文本格式、关键词拼接与 metadata。
 * <p>
 * <b>为什么用纯 JUnit 5？</b>
 * 被测类只涉及字符串拼接与 {@link FaqEntry} 转换，无外部依赖，
 * 无需启动 Spring 容器或 Mock 中间件，是典型的“无状态工具类”测试。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link org.springframework.ai.document.Document}：Spring AI 的文档模型，用于断言 id / text / metadata。</li>
 *   <li>{@link FaqEntry}：被测方法入参，使用静态工厂方法 {@code FaqEntry.of(...)} 构造。</li>
 * </ul>
 */
class FaqDocumentBuilderTest {

    @Test
    void shouldBuildDocumentWithKeywords() {
        FaqEntry entry = FaqEntry.of("怎么借书", "到馆取书。", "借书", "borrow");

        Document doc = FaqDocumentBuilder.build(entry, 0);

        assertEquals("faq:0", doc.getId());
        assertEquals("怎么借书 | 到馆取书。 | 关键词:借书 borrow", doc.getText());
        assertEquals("faq", doc.getMetadata().get("type"));
        assertEquals("怎么借书", doc.getMetadata().get("topic"));
    }

    @Test
    void shouldOmitKeywordsWhenEmpty() {
        FaqEntry entry = FaqEntry.of("还书", "归还即可。");

        Document doc = FaqDocumentBuilder.build(entry, 1);

        assertEquals("faq:1", doc.getId());
        assertEquals("还书 | 归还即可。", doc.getText());
    }

    @Test
    void shouldSkipTopicMetadataIfBlank() {
        FaqEntry entry = FaqEntry.of("", "答案", "a");

        Document doc = FaqDocumentBuilder.build(entry, 2);

        assertNull(doc.getMetadata().get("topic"));
    }
}
