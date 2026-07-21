package com.zx.admin.dashboard.service;

import com.zx.admin.dashboard.dto.BorrowOverviewResponse;
import com.zx.admin.dashboard.dto.BorrowStatisticsResponse;
import com.zx.admin.dashboard.dto.FinanceOverviewResponse;
import com.zx.admin.dashboard.dto.InventoryOverviewResponse;
import com.zx.admin.dashboard.dto.RevenueStatisticsResponse;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.bookstore.borrow.enums.BorrowOrderStatus;
import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端数据统计服务，聚合营业额、借阅、库存等图表数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TradeOrderRepository tradeOrderRepository;
    private final BorrowOrderRepository borrowOrderRepository;
    private final BookRepository bookRepository;
    private final AuthUserRepository authUserRepository;

    /**
     * 财务概览：今日营业额 + 今日订单数。
     */
    public FinanceOverviewResponse financeOverview() {
        Map<String, Object> today = tradeOrderRepository.todayRevenue();
        return new FinanceOverviewResponse(
                toBigDecimal(today.get("revenue")),
                toLong(today.get("orderCount"))
        );
    }

    /**
     * 借阅概览：今日借阅、待处理、逾期未还。
     */
    public BorrowOverviewResponse borrowOverview() {
        return new BorrowOverviewResponse(
                borrowOrderRepository.todayBorrowCount(),
                borrowOrderRepository.countAll(BorrowOrderStatus.APPLIED.name()),
                borrowOrderRepository.countAll(BorrowOrderStatus.OVERDUE.name())
        );
    }

    /**
     * 库存与用户概览：图书总数 + 用户总数。
     */
    public InventoryOverviewResponse inventoryOverview() {
        return new InventoryOverviewResponse(
                bookRepository.countAll(),
                authUserRepository.countAll()
        );
    }

    /**
     * 营业额统计趋势。
     *
     * @param days 统计最近 N 天（含今天），默认 30
     */
    public RevenueStatisticsResponse revenueStatistics(int days) {
        int safeDays = Math.min(Math.max(days, 7), 90);
        LocalDateTime start = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        List<Map<String, Object>> trendRows = tradeOrderRepository.revenueTrend(start);
        Map<String, RevenueStatisticsResponse.DailyRevenue> trendMap = new LinkedHashMap<>();
        for (Map<String, Object> row : trendRows) {
            String date = row.get("date").toString();
            BigDecimal revenue = toBigDecimal(row.get("revenue"));
            long orderCount = toLong(row.get("orderCount"));
            trendMap.put(date, new RevenueStatisticsResponse.DailyRevenue(date, revenue, orderCount));
        }

        List<RevenueStatisticsResponse.DailyRevenue> dailyRevenues = fillDates(start, safeDays, trendMap);

        BigDecimal totalRevenue = dailyRevenues.stream()
                .map(RevenueStatisticsResponse.DailyRevenue::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalOrderCount = dailyRevenues.stream()
                .mapToLong(RevenueStatisticsResponse.DailyRevenue::getOrderCount)
                .sum();

        List<Map<String, Object>> statusRows = tradeOrderRepository.revenueByStatus();
        List<RevenueStatisticsResponse.StatusAmount> byStatus = statusRows.stream()
                .map(row -> new RevenueStatisticsResponse.StatusAmount(
                        String.valueOf(row.get("status")),
                        toBigDecimal(row.get("amount")),
                        toLong(row.get("cnt"))
                ))
                .collect(Collectors.toList());

        return new RevenueStatisticsResponse(dailyRevenues, totalRevenue, totalOrderCount, byStatus);
    }

    /**
     * 借阅情况统计趋势。
     *
     * @param days 统计最近 N 天（含今天），默认 30
     */
    public BorrowStatisticsResponse borrowStatistics(int days) {
        int safeDays = Math.min(Math.max(days, 7), 90);
        LocalDateTime start = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        List<Map<String, Object>> trendRows = borrowOrderRepository.borrowTrend(start);
        Map<String, BorrowStatisticsResponse.DailyBorrow> trendMap = new LinkedHashMap<>();
        for (Map<String, Object> row : trendRows) {
            String date = row.get("date").toString();
            trendMap.put(date, new BorrowStatisticsResponse.DailyBorrow(
                    date,
                    toLong(row.get("appliedCount")),
                    toLong(row.get("borrowedCount")),
                    toLong(row.get("returnedCount")),
                    toLong(row.get("overdueCount"))
            ));
        }

        List<BorrowStatisticsResponse.DailyBorrow> dailyBorrows = fillDates(start, safeDays, trendMap);

        long totalBorrowCount = dailyBorrows.stream()
                .mapToLong(d -> d.getAppliedCount() + d.getBorrowedCount() + d.getReturnedCount() + d.getOverdueCount())
                .sum();
        long currentBorrowedCount = borrowOrderRepository.countAll(BorrowOrderStatus.BORROWED.name());
        long overdueCount = borrowOrderRepository.countAll(BorrowOrderStatus.OVERDUE.name());

        List<Map<String, Object>> statusRows = borrowOrderRepository.borrowByStatus();
        List<BorrowStatisticsResponse.StatusCount> byStatus = statusRows.stream()
                .map(row -> new BorrowStatisticsResponse.StatusCount(
                        String.valueOf(row.get("status")),
                        toLong(row.get("cnt"))
                ))
                .sorted(Comparator.comparing(BorrowStatisticsResponse.StatusCount::getCount).reversed())
                .collect(Collectors.toList());

        return new BorrowStatisticsResponse(dailyBorrows, totalBorrowCount, currentBorrowedCount, overdueCount, byStatus);
    }

    /**
     * 补全日期序列，缺失日期填充 0。
     */
    private <T> List<T> fillDates(LocalDateTime start, int days, Map<String, T> dataMap) {
        List<T> result = new ArrayList<>(days);
        LocalDate cursor = start.toLocalDate();
        for (int i = 0; i < days; i++) {
            String date = cursor.format(DATE_FMT);
            result.add(dataMap.getOrDefault(date, createEmpty(date, dataMap.values().stream().findFirst().orElse(null))));
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T createEmpty(String date, T sample) {
        if (sample instanceof RevenueStatisticsResponse.DailyRevenue) {
            return (T) new RevenueStatisticsResponse.DailyRevenue(date, BigDecimal.ZERO, 0L);
        }
        if (sample instanceof BorrowStatisticsResponse.DailyBorrow) {
            return (T) new BorrowStatisticsResponse.DailyBorrow(date, 0L, 0L, 0L, 0L);
        }
        throw new IllegalArgumentException("Unsupported type: " + (sample == null ? "null" : sample.getClass()));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
