package com.zx.reader.agent;

/**
 * Study Agent 请求级上下文：userId / ebookId 由 Service 注入，不暴露给模型传参。
 */
public final class StudyAgentContext {

    private static final ThreadLocal<Long> USER = new ThreadLocal<>();
    private static final ThreadLocal<Long> EBOOK = new ThreadLocal<>();

    private StudyAgentContext() {
    }

    public static void set(Long userId, Long ebookId) {
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
    }

    public static Long userId() {
        return USER.get();
    }

    public static Long ebookId() {
        return EBOOK.get();
    }

    public static void clear() {
        USER.remove();
        EBOOK.remove();
    }
}
