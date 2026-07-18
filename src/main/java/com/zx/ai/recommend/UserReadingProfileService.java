package com.zx.ai.recommend;

import com.zx.bookstore.borrow.entity.BorrowOrder;
import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.catalog.dto.BookCategoryResponse;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.service.BookCatalogService;
import com.zx.bookstore.trade.entity.TradeOrder;
import com.zx.bookstore.trade.entity.TradeOrderItem;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户阅读画像构建：聚合借阅（borrow_order）+ 已支付购书（trade_order PAID + items）历史，
 * 产出分类偏好、最近书目、已读书目与在借未还集合。供个性化推荐（H 板块）使用。
 * <p>
 * 不依赖向量库，纯 SQL/内存聚合。借阅按 {@link BorrowOrder#getId} 倒序视为时间倒序；
 * 购书按 {@link TradeOrder#getId} 倒序。最近书目合并两类后按 id 倒序取 TopN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserReadingProfileService {

    private static final int PROFILE_BORROW_LIMIT = 50;
    private static final int PROFILE_TRADE_LIMIT = 50;
    private static final int MAX_CATEGORIES = 5;
    private static final int MAX_RECENT = 10;

    private final BorrowOrderRepository borrowOrderRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final BookRepository bookRepository;
    private final BookCatalogService bookCatalogService;

    /**
     * 构建用户阅读画像。userId 为 null 时返回 {@link UserReadingProfile#anonymous()}。
     */
    public UserReadingProfile buildProfile(Long userId) {
        if (userId == null) {
            return UserReadingProfile.anonymous();
        }

        Set<Long> readBookIds = new LinkedHashSet<>();
        Set<Long> activeBookIds = new LinkedHashSet<>();
        List<Long> recentBookIds = new ArrayList<>();

        try {
            List<BorrowOrder> borrows = borrowOrderRepository.pageByUser(userId, null, 1, PROFILE_BORROW_LIMIT);
            for (BorrowOrder o : borrows) {
                if (o.getBookId() == null) {
                    continue;
                }
                readBookIds.add(o.getBookId());
                recentBookIds.add(o.getBookId());
                String st = o.getStatus();
                if ("APPLIED".equals(st) || "BORROWED".equals(st) || "OVERDUE".equals(st)) {
                    activeBookIds.add(o.getBookId());
                }
            }
        } catch (Exception e) {
            log.warn("build profile borrow failed, userId={}", userId, e);
        }

        try {
            List<TradeOrder> paidOrders = tradeOrderRepository.pageByUser(userId, "PAID", 1, PROFILE_TRADE_LIMIT);
            for (TradeOrder order : paidOrders) {
                List<TradeOrderItem> items = tradeOrderRepository.findItemsByOrderId(order.getId());
                for (TradeOrderItem item : items) {
                    if (item.getBookId() == null) {
                        continue;
                    }
                    readBookIds.add(item.getBookId());
                    recentBookIds.add(item.getBookId());
                }
            }
        } catch (Exception e) {
            log.warn("build profile trade failed, userId={}", userId, e);
        }

        if (readBookIds.isEmpty()) {
            log.info("build profile empty, userId={}", userId);
            return new UserReadingProfile(userId, true, List.of(), List.of(), Set.of(), Set.of());
        }

        Map<Long, Integer> categoryCount = new HashMap<>();
        for (Book book : loadBooks(readBookIds)) {
            if (book.getCategoryId() == null) {
                continue;
            }
            categoryCount.merge(book.getCategoryId(), 1, Integer::sum);
        }
        Map<Long, String> categoryName = resolveCategoryNames(categoryCount.keySet());

        List<UserReadingProfile.CategoryPreference> categories = categoryCount.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(MAX_CATEGORIES)
                .map(e -> new UserReadingProfile.CategoryPreference(
                        e.getKey(), categoryName.get(e.getKey()), e.getValue()))
                .toList();

        List<Long> recent = recentBookIds.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(MAX_RECENT)
                .toList();

        log.info("build profile userId={} read={} active={} categories={}",
                userId, readBookIds.size(), activeBookIds.size(), categories.size());
        return new UserReadingProfile(userId, true, categories, recent, readBookIds, activeBookIds);
    }

    private List<Book> loadBooks(Set<Long> bookIds) {
        List<Book> books = new ArrayList<>();
        for (Long id : bookIds) {
            Optional<Book> b = bookRepository.findById(id);
            b.ifPresent(books::add);
        }
        return books;
    }

    private Map<Long, String> resolveCategoryNames(Set<Long> categoryIds) {
        Map<Long, String> result = new HashMap<>();
        if (categoryIds.isEmpty()) {
            return result;
        }
        try {
            for (BookCategoryResponse cat : bookCatalogService.listCategories()) {
                if (categoryIds.contains(cat.getId())) {
                    result.put(cat.getId(), cat.getName());
                }
            }
        } catch (Exception e) {
            log.warn("resolve category names failed", e);
        }
        return result;
    }
}
