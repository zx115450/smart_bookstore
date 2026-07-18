package com.zx.ai.rag;

import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.service.BookCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线索引：把上架书目写入 Milvus。仅在 RAG 启用时生效。
 * <p>
 * <ul>
 *   <li>{@link #reindexAll()} 全量重建：分页扫描上架书，批量 embed + 写入。</li>
 *   <li>{@link #reindexBook(Long)} 增量刷新：删旧向量再写新向量，用于改书后同步。</li>
 * </ul>
 * 向量文档 id = {@code book:{bookId}}，metadata 含 bookId/categoryId/type/status。
 * 回查走 {@link BookCatalogService#getBookDetailAdmin}（不经布隆，含下架书，便于离线索引）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
public class BookEmbeddingIndexer {

    private static final int PAGE_SIZE = 100;
    private static final int ADD_BATCH = 32;

    private final VectorStore vectorStore;
    private final BookRepository bookRepository;
    private final BookCatalogService bookCatalogService;

    /**
     * 全量重建索引：返回写入条数。
     */
    public int reindexAll() {
        log.info("reindex all books start");
        int total = 0;
        int page = 1;
        while (true) {
            List<Book> books = bookRepository.pageEnabled(null, null, page, PAGE_SIZE);
            if (books == null || books.isEmpty()) {
                break;
            }
            List<Document> batch = new ArrayList<>(ADD_BATCH);
            for (Book book : books) {
                BookResponse view = loadView(book.getId());
                if (view == null) {
                    continue;
                }
                batch.add(BookDocumentBuilder.build(view));
                if (batch.size() >= ADD_BATCH) {
                    total += addBatch(batch);
                    batch = new ArrayList<>(ADD_BATCH);
                }
            }
            if (!batch.isEmpty()) {
                total += addBatch(batch);
            }
            if (books.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        log.info("reindex all books done, total={}", total);
        return total;
    }

    /**
     * 增量刷新单本：删旧向量再写新向量；下架/不存在则仅删除。
     */
    public void reindexBook(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return;
        }
        try {
            vectorStore.delete(List.of(BookDocumentBuilder.DOC_ID_PREFIX + bookId));
        } catch (Exception e) {
            log.warn("delete old vector failed, bookId={}", bookId, e);
        }
        BookResponse view = loadView(bookId);
        if (view == null || view.getStatus() == null || view.getStatus() != 1) {
            return;
        }
        addBatch(List.of(BookDocumentBuilder.build(view)));
        log.info("reindex single book done, bookId={}", bookId);
    }

    private BookResponse loadView(Long bookId) {
        try {
            return bookCatalogService.getBookDetailAdmin(bookId);
        } catch (Exception e) {
            log.warn("load book view failed, bookId={}", bookId, e);
            return null;
        }
    }

    private int addBatch(List<Document> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            vectorStore.add(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("vectorStore.add failed, size={}", batch.size(), e);
            return 0;
        }
    }
}
