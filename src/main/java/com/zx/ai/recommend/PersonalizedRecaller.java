package com.zx.ai.recommend;

import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个性化召回（H 板块）：基于用户阅读画像召回候选 bookId。
 * <p>
 * 召回策略（两路合并，去重）：
 * <ol>
 *   <li>同分类未借过：取用户偏好分类（按命中次数降序），在每个分类下召回可借图书，
 *       排除用户已读过的书（{@link UserReadingProfile#}）；</li>
 *   <li>简化共现：借过同样书的其他用户还借过什么（{@link BorrowOrderRepository#findCoBorrowedBooks}），
 *       排除用户已读过的书。</li>
 * </ol>
 * 不依赖向量库，纯 SQL/内存聚合。返回候选 bookId（按召回来源加权降序），
 * 由 {@link BookRecommendService} 回查 MySQL 补架位/库存并重排序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedRecaller {

    private static final int CANDIDATE_POOL = 50;
    private static final int PER_CATEGORY_LIMIT = 20;
    private static final int CO_BORROW_LIMIT = 30;
    private static final int WEIGHT_CATEGORY = 2;
    private static final int WEIGHT_CO_BORROW = 3;

    private final BookRepository bookRepository;
    private final BorrowOrderRepository borrowOrderRepository;

    /**
     * 个性化召回候选 bookId。
     *
     * @param profile 用户阅读画像（须已登录且有历史）
     * @return 候选 bookId 列表（按加权分降序），无结果返回空
     */
    public List<Long> recall(UserReadingProfile profile) {
        if (profile == null || !profile.loggedIn() || profile.readBookIds().isEmpty()) {
            return List.of();
        }
        Set<Long> readBookIds = profile.readBookIds();
        Map<Long, Integer> score = new HashMap<>();

        // 1. 同分类未借过
        for (UserReadingProfile.CategoryPreference cat : profile.categories()) {
            try {
                List<Book> books = bookRepository.pageEnabled(cat.categoryId(), null, 1, PER_CATEGORY_LIMIT);
                for (Book b : books) {
                    if (b.getId() == null || readBookIds.contains(b.getId())) {
                        continue;
                    }
                    if (b.getBorrowStock() == null || b.getBorrowStock() <= 0) {
                        continue;
                    }
                    score.merge(b.getId(), WEIGHT_CATEGORY, Integer::sum);
                }
            } catch (Exception e) {
                log.warn("personalized category recall failed, categoryId={}", cat.categoryId(), e);
            }
        }

        // 2. 简化共现：传入目标用户 id，由 mapper 用 b1.user_id <> #{userId} 排除目标用户自身
        try {
            List<Map<String, Object>> rows = borrowOrderRepository.findCoBorrowedBooks(
                    profile.userId(), List.copyOf(readBookIds), CO_BORROW_LIMIT);
            for (Map<String, Object> row : rows) {
                Long bookId = toLong(row.get("bookId"));
                if (bookId == null || readBookIds.contains(bookId)) {
                    continue;
                }
                int heat = toInt(row.get("heat"));
                score.merge(bookId, WEIGHT_CO_BORROW * Math.max(1, heat), Integer::sum);
            }
        } catch (Exception e) {
            log.warn("personalized co-borrow recall failed", e);
        }

        if (score.isEmpty()) {
            log.info("personalized recall empty");
            return List.of();
        }
        List<Long> ranked = score.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(CANDIDATE_POOL)
                .map(Map.Entry::getKey)
                .toList();
        log.info("personalized recall hit={}", ranked.size());
        return ranked;
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
