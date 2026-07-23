package com.zx.ai.rag;

import com.zx.bookstore.catalog.dto.BookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookDocumentBuilder 的纯单元测试：验证 RAG 文档的文本格式、metadata 与截断逻辑。
 * <p>
 * <b>为什么用纯 JUnit 5？</b>
 * 被测类只涉及简单字符串拼接与对象转换，没有外部依赖，
 * 无需启动 Spring 容器或 Mock 任何 Repository，因此是最快的单元测试。
 * <p>
 * <b>依赖说明：</b>
 * <ul>
 *   <li>{@link org.springframework.ai.document.Document}：来自 Spring AI，是测试中断言的对象类型。</li>
 *   <li>{@link BookResponse}：被测方法入参，只使用 POJO 的 setter 构建测试数据。</li>
 * </ul>
 */
class BookDocumentBuilderTest {

    @Test
    void shouldBuildDocumentWithExpectedTextAndMetadata() {
        BookResponse book = new BookResponse();
        book.setId(1L);
        book.setTitle("Spring in Action");
        book.setAuthor("Craig Walls");
        book.setCategoryName("技术");
        book.setDescription("经典 Spring 教程。");
        book.setStatus(1);

        Document doc = BookDocumentBuilder.build(book);

        assertEquals("book:1", doc.getId());
        assertEquals("Spring in Action | Craig Walls | 分类:技术 | 经典 Spring 教程。", doc.getText());
        assertEquals(1L, doc.getMetadata().get("bookId"));
        assertEquals("book", doc.getMetadata().get("type"));
        assertEquals(1, doc.getMetadata().get("status"));
    }

    @Test
    void shouldHandleMissingOptionalFields() {
        BookResponse book = new BookResponse();
        book.setId(2L);
        book.setTitle("极简");
        book.setAuthor(null);
        book.setCategoryName(null);
        book.setDescription(null);

        Document doc = BookDocumentBuilder.build(book);

        assertEquals("book:2", doc.getId());
        assertEquals("极简 | ", doc.getText());
        assertNull(doc.getMetadata().get("categoryId"));
        assertNull(doc.getMetadata().get("status"));
    }

    @Test
    void shouldTruncateLongDescription() {
        BookResponse book = new BookResponse();
        book.setId(3L);
        book.setTitle("T");
        book.setAuthor("A");
        book.setDescription("A".repeat(600));

        Document doc = BookDocumentBuilder.build(book);

        assertTrue(doc.getText().endsWith("..."));
        assertEquals(BookDocumentBuilder.MAX_DESC_LEN + 3, doc.getText().split(" \\| ")[2].length());
    }
}
