package com.zx.ai.recommend;

import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类图书召回：在指定分类下返回可借（status=1 且 borrowStock>0）的图书 bookId。
 * <p>
 * 不依赖向量库，按 categoryId 过滤 + borrowStock>0，取较多候选交由上层重排序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryBookRecaller {

    private static final int CANDIDATE_POOL = 50;

    private final BookRepository bookRepository;

    /**
     * 返回分类下可借图书 bookId（最多 CANDIDATE_POOL 个），按 id 倒序。
     */
    public List<Long> recall(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return List.of();
        }
        try {
            List<Book> books = bookRepository.pageEnabled(categoryId, null, 1, CANDIDATE_POOL);
            return books.stream()
                    .filter(b -> b.getBorrowStock() != null && b.getBorrowStock() > 0)
                    .map(Book::getId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("category recall failed, categoryId={}", categoryId, e);
            return List.of();
        }
    }

    /**
     * 关键词召回（F 阶段简化版）：按书名 keyword 过滤可借图书，作为 EXPLORE/LEARN 兜底。
     */
    public List<Long> recallByKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        try {
            List<Book> books = bookRepository.pageEnabled(null, keyword.trim(), 1, CANDIDATE_POOL);
            return books.stream()
                    .filter(b -> b.getBorrowStock() != null && b.getBorrowStock() > 0)
                    .map(Book::getId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("keyword recall failed, keyword={}", keyword, e);
            return List.of();
        }
    }
}
