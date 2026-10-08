package com.zx.reader.agent;

import java.util.Collection;
import java.util.List;

/**
 * Study Agent 请求级上下文：userId / ebookId / roles 由 Service 注入，不暴露给模型传参。
 */
public final class StudyAgentContext {

    private static final ThreadLocal<Long> USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> EBOOK = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();

    private StudyAgentContext() {
    }

    public static void set(Long userId, Long ebookId) {
        set(userId, ebookId, List.of());
    }

    public static void set(Long userId, Long ebookId, Collection<String> roles) {
        if (userId == null) {
            USER.remove();
        } else {
            USER.set(userId);
        }
        if (ebookId == null) {
            EBOOK.remove();
        } else {
            EBOOK.set(ebookId);
        }
        ROLES.set(roles == null ? List.of() : List.copyOf(roles));
    }

    public static Long userId() {
        return USER.get();
    }

    public static Long ebookId() {
        return EBOOK.get();
    }

    public static List<String> roles() {
        List<String> roles = ROLES.get();
        return roles == null ? List.of() : roles;
    }

    public static void clear() {
        USER.remove();
        EBOOK.remove();
        ROLES.remove();
    }
}
