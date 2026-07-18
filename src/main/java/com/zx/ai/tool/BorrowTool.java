package com.zx.ai.tool;

import com.zx.ai.support.AiUserContext;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.borrow.dto.BorrowOrderResponse;
import com.zx.bookstore.borrow.service.BorrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人借阅 Tool 适配层：查询当前登录用户自己的借阅订单。
 * <p>
 * userId 来自 {@link AiUserContext}（由 AiChatService 从 JWT 注入），
 * 不通过 @ToolParam 暴露给 LLM，防止用户伪造他人 id。
 * 未登录时返回提示，由 LLM 引导用户先登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowTool {

    private final BorrowService borrowService;

    @Tool(
            name = "getMyBorrowOrders",
            description = "查询当前登录用户自己的借阅订单，用于回答「我借的书在哪取」「我有什么待还」「我的借阅状态」等个人借阅问题。可选 status 过滤：APPLIED(待取书)、BORROWED(借阅中)、OVERDUE(逾期)、RETURNED(已还)、CANCELLED(已取消)。未登录时返回提示，需引导用户先登录。不要用于查别人的订单。"
    )
    public Map<String, Object> getMyBorrowOrders(
            @ToolParam(required = false, description = "状态过滤，可选值：APPLIED/BORROWED/OVERDUE/RETURNED/CANCELLED；不传则查全部") String status
    ) {
        return AiUserContext.userId()
                .map(userId -> doQuery(userId, status))
                .orElseGet(this::notLoggedIn);
    }

    private Map<String, Object> doQuery(Long userId, String status) {
        String safeStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        AuthPrincipal principal = new AuthPrincipal(userId, null, null, List.of());

        List<BorrowOrderResponse> orders = borrowService
                .listMyOrders(principal, safeStatus, 1, 10)
                .getRecords();

        Map<String, Object> result = new LinkedHashMap<>();
        if (orders.isEmpty()) {
            result.put("found", false);
            result.put("total", 0);
            result.put("orders", List.of());
            result.put("message", safeStatus == null
                    ? "您当前没有任何借阅订单"
                    : "您当前没有状态为 " + safeStatus + " 的借阅订单");
            log.info("tool getMyBorrowOrders userId={} status={} empty", userId, safeStatus);
            return result;
        }

        List<Map<String, Object>> slim = orders.stream()
                .map(BorrowTool::toView)
                .toList();
        result.put("found", true);
        result.put("total", slim.size());
        result.put("orders", slim);
        log.info("tool getMyBorrowOrders userId={} status={} hit={}", userId, safeStatus, slim.size());
        return result;
    }

    private Map<String, Object> notLoggedIn() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", false);
        result.put("total", 0);
        result.put("orders", List.of());
        result.put("message", "未登录，无法查询个人借阅，请引导用户先登录后再试");
        log.info("tool getMyBorrowOrders anonymous blocked");
        return result;
    }

    private static Map<String, Object> toView(BorrowOrderResponse o) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderNo", o.getOrderNo());
        view.put("bookTitle", o.getBookTitle());
        view.put("status", o.getStatus());
        view.put("shelfLocation", o.getShelfLocation());
        view.put("borrowAt", o.getBorrowAt());
        view.put("dueAt", o.getDueAt());
        view.put("returnAt", o.getReturnAt());
        return view;
    }
}
