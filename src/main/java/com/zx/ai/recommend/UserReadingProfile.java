package com.zx.ai.recommend;

import java.util.List;
import java.util.Set;

/**
 * 用户阅读画像：聚合借阅 + 购书历史得出分类偏好与已读书目，供个性化推荐（H 板块）使用。
 * <p>
 * 由 {@link UserReadingProfileService} 构建；{@link PersonalizedRecaller} 据此召回候选，
 * {@link BookRecommendService} 据此排除已借未还/已读书目。
 *
 * @param userId          用户 id（未登录为 null）
 * @param loggedIn        是否登录（false 时其余字段均为空，调用方应引导登录）
 * @param categories      分类偏好（按命中次数降序，最多 N 个）
 * @param recentBookIds   最近借阅/购书的 bookId（按时间倒序，最多 N 个）
 * @param readBookIds     用户历史读过（借过或买过）的全部 bookId，用于个性化召回排除「已读过」
 * @param activeBookIds   当前在借未还（APPLIED/BORROWED/OVERDUE）的 bookId，用于推荐排除
 */
public record UserReadingProfile(
        Long userId,
        boolean loggedIn,
        List<CategoryPreference> categories,
        List<Long> recentBookIds,
        Set<Long> readBookIds,
        Set<Long> activeBookIds
) {

    /**
     * 分类偏好项。
     *
     * @param categoryId   分类 id
     * @param categoryName  分类名（可能为 null）
     * @param count         用户在该分类下的借阅/购书次数
     */
    public record CategoryPreference(Long categoryId, String categoryName, int count) {
    }

    /**
     * 未登录的空画像。
     */
    public static UserReadingProfile anonymous() {
        return new UserReadingProfile(null, false, List.of(), List.of(), Set.of(), Set.of());
    }
}
