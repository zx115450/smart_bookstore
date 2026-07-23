package com.zx.ai.recommend;

import com.zx.ai.support.AiUserContext;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.service.BookCatalogService;
import com.zx.bookstore.exception.BookstoreException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 推荐编排入口：召回 → 加热 → 回查 MySQL 补架位/库存 → 重排序 → 生成理由。
 * <p>
 * F 阶段：规则召回（热度 + 分类 + 关键词）。
 * G 阶段：当 RAG 启用时，LEARN/SIMILAR 意图优先走 {@link SemanticBookRecaller} 语义召回，
 *         规则召回作为兜底；SemanticBookRecaller 不存在（RAG 关闭）时自动降级为纯规则。
 * H 阶段：新增 PERSONALIZED 意图——基于 {@link UserReadingProfileService} 构建画像，
 *         由 {@link PersonalizedRecaller} 召回（同分类未借过 + 简化共现）；
 *         SIMILAR 意图支持 seedBookId（基于种子书的分类 + 语义召回）。
 *         推荐结果排除用户当前在借未还（activeBookIds）与已下架书。
 * 重排序优先级：可借（borrowStock>0） > 有架位（shelfLocation 非空） > 热度。
 * <p>
 * 对外由 {@link com.zx.ai.tool.BookRecommendTool} 以 {@code recommendBooks} Tool 暴露给 LLM；
 * 本类不直接处理 HTTP / Prompt，只负责「候选书 ID 列表 → RecommendItem」。
 */
@Slf4j
@Service
public class BookRecommendService {

    /** 默认返回本数（Tool 未传 limit 时）。 */
    private static final int DEFAULT_LIMIT = 3;
    /** 单次推荐上限，避免卡片过多。 */
    private static final int MAX_LIMIT = 5;
    /** 召回阶段候选池大小；精排后再截到 topN。 */
    private static final int CANDIDATE_POOL = 50;

    private final HotBookRecaller hotBookRecaller;
    private final CategoryBookRecaller categoryBookRecaller;
    private final BookCatalogService bookCatalogService;
    /**
     * 语义召回（Milvus）。用 ObjectProvider 而非强制注入：
     * {@code ai.rag.enabled=false} 时 Bean 不存在，getIfAvailable() 为 null，自动降级为规则召回。
     */
    private final ObjectProvider<SemanticBookRecaller> semanticBookRecallerProvider;
    private final UserReadingProfileService userReadingProfileService;
    private final PersonalizedRecaller personalizedRecaller;

    public BookRecommendService(HotBookRecaller hotBookRecaller,
                                CategoryBookRecaller categoryBookRecaller,
                                BookCatalogService bookCatalogService,
                                ObjectProvider<SemanticBookRecaller> semanticBookRecallerProvider,
                                UserReadingProfileService userReadingProfileService,
                                PersonalizedRecaller personalizedRecaller) {
        this.hotBookRecaller = hotBookRecaller;
        this.categoryBookRecaller = categoryBookRecaller;
        this.bookCatalogService = bookCatalogService;
        this.semanticBookRecallerProvider = semanticBookRecallerProvider;
        this.userReadingProfileService = userReadingProfileService;
        this.personalizedRecaller = personalizedRecaller;
    }

    /**
     * 推荐图书（编排主流程）。
     * <ol>
     *   <li>按 intent 分支做多路召回，得到 candidateIds</li>
     *   <li>候选为空时用热门补足</li>
     *   <li>回查 MySQL 补架位/库存，过滤下架与在借未还</li>
     *   <li>按「可借 &gt; 有架位 &gt; 热度」重排，截断到 topN</li>
     *   <li>为每本书生成 recommendReason</li>
     * </ol>
     *
     * @param intent     意图：EXPLORE / LEARN / SIMILAR / RELAX / PERSONALIZED
     * @param query      可选关键词/自然语言（LEARN/SIMILAR 时优先语义召回）
     * @param categoryId 可选分类 ID（指定分类时优先在该分类内推荐）
     * @param seedBookId 可选种子书 ID（SIMILAR 时基于此书找相似）
     * @param limit      返回数量，默认 3，最大 5
     * @return 精排后的推荐项；PERSONALIZED 未登录或无候选时返回空列表
     */
    public List<RecommendItem> recommend(String intent, String query, Long categoryId, Long seedBookId, Integer limit) {
        int topN = clamp(limit, DEFAULT_LIMIT, MAX_LIMIT);
        String normIntent = normalizeIntent(intent);

        // PERSONALIZED 需登录；未登录返回空，由 BookRecommendTool 把 message 转成「请先登录」
        if ("PERSONALIZED".equals(normIntent) && !AiUserContext.isLoggedIn()) {
            log.info("recommend PERSONALIZED but anonymous, blocked");
            return List.of();
        }

        // ---------- 阶段 1：按意图召回候选 bookId ----------
        List<Long> candidateIds = new ArrayList<>();
        // PERSONALIZED 时排除「当前在借未还」，避免推荐用户手里已有的书
        Set<Long> excludeIds = Set.of();
        // 是否真正走出了个性化路径（有画像且有阅读历史）；影响理由文案
        boolean personalized = false;

        if ("PERSONALIZED".equals(normIntent)) {
            // H：画像 → 个性化召回；不足再用偏好分类补
            Long userId = AiUserContext.userId().orElse(null);
            UserReadingProfile profile = userReadingProfileService.buildProfile(userId);
            personalized = profile.loggedIn() && !profile.readBookIds().isEmpty();
            if (personalized) {
                excludeIds = profile.activeBookIds();
                for (Long id : personalizedRecaller.recall(profile)) {
                    if (!candidateIds.contains(id)) {
                        candidateIds.add(id);
                    }
                }
                // 个性化召回不足时：按偏好分类拉上架书，且跳过已读过的
                if (candidateIds.size() < CANDIDATE_POOL) {
                    for (UserReadingProfile.CategoryPreference cat : profile.categories()) {
                        for (Long id : categoryBookRecaller.recall(cat.categoryId())) {
                            if (!profile.readBookIds().contains(id) && !candidateIds.contains(id)) {
                                candidateIds.add(id);
                            }
                        }
                    }
                }
            }
            // 登录但无阅读历史时 personalized=false，后面会走热门兜底
        } else if ("SIMILAR".equals(normIntent) && seedBookId != null) {
            // H：指定种子书找相似 —— 同分类规则召回 +（可选）种子文本语义召回
            Long seedCategory = resolveCategory(seedBookId);
            if (seedCategory != null) {
                for (Long id : categoryBookRecaller.recall(seedCategory)) {
                    // 排除种子书自身
                    if (!seedBookId.equals(id) && !candidateIds.contains(id)) {
                        candidateIds.add(id);
                    }
                }
            }
            // G：把种子书压成短文本，进 Milvus 找语义相近书（RAG 关闭则跳过）
            String seedText = buildSeedQuery(seedBookId);
            if (StringUtils.hasText(seedText)) {
                SemanticBookRecaller semantic = semanticBookRecallerProvider.getIfAvailable();
                if (semantic != null) {
                    for (Long id : semantic.recall(seedText, CANDIDATE_POOL)) {
                        if (!seedBookId.equals(id) && !candidateIds.contains(id)) {
                            candidateIds.add(id);
                        }
                    }
                }
            }
        } else {
            // EXPLORE / LEARN / RELAX / SIMILAR(无 seed) 等通用路径
            // G：LEARN/SIMILAR 且带 query 时优先语义召回（模糊主题比关键词更靠谱）
            if (StringUtils.hasText(query) && ("LEARN".equals(normIntent) || "SIMILAR".equals(normIntent))) {
                SemanticBookRecaller semantic = semanticBookRecallerProvider.getIfAvailable();
                if (semantic != null) {
                    List<Long> sem = semantic.recall(query, CANDIDATE_POOL);
                    for (Long id : sem) {
                        if (!candidateIds.contains(id)) {
                            candidateIds.add(id);
                        }
                    }
                }
            }

            // 指定分类：在分类内召回上架可借书
            if (categoryId != null && categoryId > 0) {
                for (Long id : categoryBookRecaller.recall(categoryId)) {
                    if (!candidateIds.contains(id)) {
                        candidateIds.add(id);
                    }
                }
            }

            // 候选仍不足：用书名/作者等关键词 SQL 模糊补（F 阶段兜底）
            if (candidateIds.size() < CANDIDATE_POOL && StringUtils.hasText(query)) {
                for (Long id : categoryBookRecaller.recallByKeyword(query)) {
                    if (!candidateIds.contains(id)) {
                        candidateIds.add(id);
                    }
                }
            }
        }

        // 各路召回都空（如 EXPLORE 无参数、个性化无历史）：用全馆热门补足
        if (candidateIds.isEmpty()) {
            for (HotBookStat s : hotBookRecaller.recall(CANDIDATE_POOL)) {
                candidateIds.add(s.bookId());
            }
        }

        if (candidateIds.isEmpty()) {
            log.info("recommend empty, intent={} categoryId={} query={} seed={}",
                    normIntent, categoryId, query, seedBookId);
            return List.of();
        }

        // ---------- 阶段 2：加热 + 回查业务库（向量/规则只给 ID，真相在 MySQL） ----------
        Map<Long, Integer> heatMap = hotBookRecaller.heatMapFor(candidateIds);

        Map<Long, BookResponse> books = new LinkedHashMap<>();
        for (Long id : candidateIds) {
            if (excludeIds.contains(id)) {
                continue; // 排除当前在借未还
            }
            try {
                BookResponse resp = bookCatalogService.getBookDetail(id);
                // status==1 表示上架；向量索引可能滞后，这里再挡一道
                if (resp != null && resp.getStatus() != null && resp.getStatus() == 1) {
                    books.put(id, resp);
                }
            } catch (BookstoreException e) {
                // 已下架 / 不存在，跳过
            } catch (Exception e) {
                log.warn("recommend load book failed, id={}", id, e);
            }
        }
        if (books.isEmpty()) {
            return List.of();
        }

        // ---------- 阶段 3：重排 —— 可借优先，再有架位，再热度高 ----------
        List<Long> ranked = books.keySet().stream()
                .sorted(Comparator
                        .comparing((Long id) -> borrowable(books.get(id)) ? 0 : 1)
                        .thenComparing(id -> hasShelf(books.get(id)) ? 0 : 1)
                        .thenComparing(id -> heatMap.getOrDefault(id, 0), Comparator.reverseOrder()))
                .toList();

        String categoryName = resolveCategoryName(categoryId);
        // 用于理由文案：是否可能走过语义召回（非个性化 + LEARN/SIMILAR + 有 query + RAG 开）
        boolean usedSemantic = !personalized
                && StringUtils.hasText(query)
                && ("LEARN".equals(normIntent) || "SIMILAR".equals(normIntent))
                && semanticBookRecallerProvider.getIfAvailable() != null;

        // ---------- 阶段 4：截断 topN + 生成中文推荐理由 ----------
        List<RecommendItem> result = new ArrayList<>(topN);
        for (Long id : ranked) {
            if (result.size() >= topN) {
                break;
            }
            BookResponse book = books.get(id);
            int heat = heatMap.getOrDefault(id, 0);
            result.add(new RecommendItem(book, heat,
                    buildReason(book, heat, categoryName, query, usedSemantic, personalized, normIntent, seedBookId)));
        }
        log.info("recommend intent={} categoryId={} query={} seed={} personalized={} -> {} items",
                normIntent, categoryId, query, seedBookId, personalized, result.size());
        return result;
    }

    /**
     * 规范化意图字符串；未知值回落为 EXPLORE（随便看看 / 热门兜底）。
     */
    private String normalizeIntent(String intent) {
        String s = StringUtils.hasText(intent) ? intent.trim().toUpperCase() : "EXPLORE";
        return switch (s) {
            case "LEARN", "SIMILAR", "RELAX", "EXPLORE", "PERSONALIZED" -> s;
            default -> "EXPLORE";
        };
    }

    /**
     * 查种子书所属分类，供 SIMILAR 做「同分类」规则召回。
     */
    private Long resolveCategory(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return null;
        }
        try {
            return bookCatalogService.getBookDetailAdmin(bookId).getCategoryId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将种子书压成短文本，供 SIMILAR 意图下的语义召回使用（书名 + 分类 + 简介截断）。
     */
    private String buildSeedQuery(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return null;
        }
        try {
            BookResponse b = bookCatalogService.getBookDetailAdmin(bookId);
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(b.getTitle())) {
                sb.append(b.getTitle());
            }
            if (StringUtils.hasText(b.getCategoryName())) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(b.getCategoryName());
            }
            if (StringUtils.hasText(b.getDescription())) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(b.getDescription(), 0, Math.min(b.getDescription().length(), 120));
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把 categoryId 转成分类名，写入推荐理由（如「属于『计算机』分类」）。
     */
    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return null;
        }
        try {
            return bookCatalogService.listCategories().stream()
                    .filter(c -> Objects.equals(c.getId(), categoryId))
                    .map(c -> c.getName())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 拼推荐理由文案，供 LLM / 前端卡片展示；按意图附加「个性化」「相似」「语义相关」等说明。
     * 多段用中文逗号拼接；若无任何标签则回落到「当前可借阅 / 馆内在架」。
     */
    private String buildReason(BookResponse book, int heat, String categoryName, String query,
                                boolean usedSemantic, boolean personalized, String intent, Long seedBookId) {
        List<String> parts = new ArrayList<>();
        if (personalized) {
            parts.add("根据你的借阅/购书记录推荐");
        }
        if ("SIMILAR".equals(intent) && seedBookId != null) {
            parts.add("与图书 #" + seedBookId + " 同类或相关");
        }
        if (StringUtils.hasText(categoryName)) {
            parts.add("属于「" + categoryName + "」分类");
        }
        if (StringUtils.hasText(query) && !personalized) {
            parts.add(usedSemantic
                    ? "与「" + query + "」语义相关"
                    : "与「" + query + "」相关");
        }
        if (heat > 0) {
            parts.add("本馆借阅/购买热度较高");
        }
        if (parts.isEmpty()) {
            int borrow = book.getBorrowStock() == null ? 0 : book.getBorrowStock();
            parts.add(borrow > 0 ? "当前可借阅" : "馆内在架");
        }
        return String.join("，", parts);
    }

    /** 是否当前可借（借阅库存 &gt; 0）。 */
    private static boolean borrowable(BookResponse book) {
        return book != null && book.getBorrowStock() != null && book.getBorrowStock() > 0;
    }

    /** 是否已绑定架位（有助于用户到馆取书）。 */
    private static boolean hasShelf(BookResponse book) {
        return book != null && StringUtils.hasText(book.getShelfLocation());
    }

    /** 将 limit 限制在 [1, max]，null 时用默认值。 */
    private static int clamp(Integer limit, int def, int max) {
        if (limit == null) return def;
        return Math.min(Math.max(limit, 1), max);
    }
}
