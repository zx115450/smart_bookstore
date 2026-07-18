package com.zx.ai.recommend;

import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 热门图书召回：合并借阅单数（borrow_order）与已支付销量（trade_order_item + trade_order PAID）。
 * <p>
 * 借阅单数权重 1，销量权重 2（购书意愿更强），合并后按 heat 降序取 TopN。
 * 不依赖向量库，纯 SQL 聚合，F 阶段 MVP。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotBookRecaller {

    private static final int BORROW_WEIGHT = 1;
    private static final int TRADE_WEIGHT = 2;

    private final BorrowOrderRepository borrowOrderRepository;
    private final TradeOrderRepository tradeOrderRepository;

    /**
     * 全馆热门图书 TopN。
     */
    public List<HotBookStat> recall(int limit) {
        Map<Long, Integer> heat = new HashMap<>();
        try {
            for (Map<String, Object> row : borrowOrderRepository.findHotBorrowBooks(limit)) {
                HotBookStat s = HotBookStat.from(row);
                if (s.bookId() != null) {
                    heat.merge(s.bookId(), s.heat() * BORROW_WEIGHT, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("hot borrow recall failed", e);
        }
        try {
            for (Map<String, Object> row : tradeOrderRepository.findHotPaidBooks(limit)) {
                HotBookStat s = HotBookStat.from(row);
                if (s.bookId() != null) {
                    heat.merge(s.bookId(), s.heat() * TRADE_WEIGHT, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("hot trade recall failed", e);
        }
        return heat.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> new HotBookStat(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 给定候选 bookId 的热度映射（缺失则 heat=0），用于分类召回后的重排序。
     */
    public Map<Long, Integer> heatMapFor(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> all = new HashMap<>();
        for (HotBookStat s : recall(200)) {
            all.put(s.bookId(), s.heat());
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Long id : bookIds) {
            result.put(id, all.getOrDefault(id, 0));
        }
        return result;
    }
}
