package com.zx.ai.support;

import java.util.Optional;

/**
 * AI 请求级用户上下文：持有当前登录用户 id（匿名时为空）。
 * <p>
 * 由 {@code AiChatService} 在请求入口写入、结束时清空，
 * 供个人借阅等 Tool 读取，避免让 LLM 通过 @ToolParam 传入 userId（防伪造）。
 */
public final class AiUserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private AiUserContext() {
    }

    public static void setUserId(Long userId) {
        if (userId == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(userId);
        }
    }

    public static Optional<Long> userId() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static boolean isLoggedIn() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
