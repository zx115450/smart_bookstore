package com.zx.ai.tool;

import com.zx.ai.recommend.UserReadingProfile;
import com.zx.ai.recommend.UserReadingProfileService;
import com.zx.ai.support.AiUserContext;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.service.BookCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户阅读画像 Tool：返回当前登录用户的分类偏好与最近读过的书目，
 * 供 LLM 理解用户兴趣、决定是否调用 {@code recommendBooks(intent=PERSONALIZED)}。
 * <p>
 * userId 来自 {@link AiUserContext}（由 AiChatService 从 JWT 注入），不通过 @ToolParam 暴露，
 * 防止 LLM 伪造他人 id。未登录时返回提示，由 LLM 引导用户先登录。
 * <p>
 * 不直接产出推荐结果——推荐仍走 {@code recommendBooks}；本工具仅提供画像信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserReadingProfileTool {

    private static final int RECENT_TITLE_LIMIT = 5;

    private final UserReadingProfileService profileService;
    private final BookCatalogService bookCatalogService;

    @Tool(
            name = "getUserReadingProfile",
            description = "查询当前登录用户的阅读画像（分类偏好 + 最近读过的书），用于「根据我借过的推荐」「我的兴趣是什么」等个性化场景。未登录时返回提示，需引导用户先登录。本工具只返回画像，不直接产出推荐；拿到画像后应调用 recommendBooks(intent=PERSONALIZED) 获取推荐结果。"
    )
    public Map<String, Object> getUserReadingProfile() {
        return AiUserContext.userId()
                .map(this::doBuild)
                .orElseGet(this::notLoggedIn);
    }

    private Map<String, Object> doBuild(Long userId) {
        UserReadingProfile profile = profileService.buildProfile(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loggedIn", true);
        result.put("userId", userId);

        List<Map<String, Object>> categories = new ArrayList<>();
        for (UserReadingProfile.CategoryPreference c : profile.categories()) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("categoryId", c.categoryId());
            cat.put("categoryName", c.categoryName());
            cat.put("count", c.count());
            categories.add(cat);
        }
        result.put("preferredCategories", categories);
        result.put("readCount", profile.readBookIds().size());
        result.put("activeBorrowCount", profile.activeBookIds().size());

        // 最近读过的书名（便于 LLM 引用）
        List<Map<String, Object>> recent = new ArrayList<>();
        int idx = 0;
        for (Long bookId : profile.recentBookIds()) {
            if (idx++ >= RECENT_TITLE_LIMIT) {
                break;
            }
            try {
                BookResponse b = bookCatalogService.getBookDetailAdmin(bookId);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("bookId", b.getId());
                r.put("title", b.getTitle());
                r.put("categoryName", b.getCategoryName());
                recent.add(r);
            } catch (Exception e) {
                // 已下架/不存在则跳过
            }
        }
        result.put("recentBooks", recent);

        log.info("tool getUserReadingProfile userId={} categories={} recent={}",
                userId, categories.size(), recent.size());
        return result;
    }

    private Map<String, Object> notLoggedIn() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loggedIn", false);
        result.put("message", "未登录，无法获取阅读画像，请引导用户先登录后再试");
        log.info("tool getUserReadingProfile anonymous blocked");
        return result;
    }
}
