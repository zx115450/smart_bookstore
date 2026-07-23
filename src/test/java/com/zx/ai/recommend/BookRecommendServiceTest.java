package com.zx.ai.recommend;

import com.zx.ai.support.AiUserContext;
import com.zx.bookstore.catalog.dto.BookCategoryResponse;
import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.service.BookCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BookRecommendService 的纯单元测试：验证推荐编排（多路召回、去重、回查 MySQL、重排序、推荐理由）。
 * <p>
 * <b>为什么用 {@code @ExtendWith(MockitoExtension.class)}？</b>
 * 这是 JUnit 5 的扩展机制，告诉 JUnit 在运行测试前让 Mockito 初始化本类中带
 * {@code @Mock} 和 {@code @InjectMocks} 的字段。它<strong>不启动 Spring 容器</strong>，
 * 因此测试只测当前类的业务逻辑，速度快、不依赖 Milvus / Redis / MySQL。
 * <p>
 * <b>依赖替身说明：</b>
 * <ul>
 *   <li>{@code @Mock}：生成一个指定接口/类的“假对象”，默认方法返回 null / 0 / false，
 *       用 {@code when(...).thenReturn(...)} 可以预先约定返回值。</li>
 *   <li>{@code @InjectMocks}：Mockito 会按类型把本类里的 {@code @Mock} 注入到被测对象
 *       {@link BookRecommendService} 的构造参数或字段中。</li>
 *   <li>{@code ObjectProvider<SemanticBookRecaller>}：Spring 中用来容忍 Bean 缺失。
 *       测试中我们用 Mockito 生成一个假的 ObjectProvider，
 *       {@code getIfAvailable()} 返回 null 即可模拟 RAG 关闭时的降级路径。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BookRecommendServiceTest {

    // ===== 被测对象的外部依赖：全部用 @Mock 替换 =====
    @Mock
    private HotBookRecaller hotBookRecaller;
    @Mock
    private CategoryBookRecaller categoryBookRecaller;
    @Mock
    private BookCatalogService bookCatalogService;
    @Mock
    private ObjectProvider<SemanticBookRecaller> semanticBookRecallerProvider;
    @Mock
    private UserReadingProfileService userReadingProfileService;
    @Mock
    private PersonalizedRecaller personalizedRecaller;

    // 被测对象：Mockito 自动把上面的 Mock 注入构造参数
    @InjectMocks
    private BookRecommendService recommendService;

    /**
     * 每个测试前设置一个已登录用户上下文，供 PERSONALIZED 意图使用。
     * AiUserContext 是 ThreadLocal，必须在 @AfterEach 中清理，避免污染其他用例。
     */
    @BeforeEach
    void setUpUser() {
        AiUserContext.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        AiUserContext.clear();
    }

    /**
     * 匿名用户请求 PERSONALIZED 推荐 → 应返回空列表，不调用任何召回器。
     * 这是 BookRecommendService 的自我保护：Tool 层会把空结果转成“请先登录”提示。
     */
    @Test
    void personalized_shouldReturnEmptyForAnonymous() {
        AiUserContext.clear();

        List<RecommendItem> result = recommendService.recommend("PERSONALIZED", null, null, null, 3);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userReadingProfileService, personalizedRecaller, categoryBookRecaller);
    }

    /**
     * EXPLORE 无参数时，分类召回和语义召回都无结果，应走全馆热门兜底。
     * 验证：返回热门书、按热度写入推荐理由、数量为限制后的 topN。
     */
    @Test
    void explore_shouldFallBackToHotBooks() {
        // EXPLORE 无 query 也无 categoryId，不会进入分类召回，因此无需 stub categoryBookRecaller
        when(hotBookRecaller.recall(50)).thenReturn(List.of(
                new HotBookStat(1L, 100),
                new HotBookStat(2L, 80)
        ));
        when(hotBookRecaller.heatMapFor(List.of(1L, 2L))).thenReturn(Map.of(1L, 100, 2L, 80));
        when(bookCatalogService.getBookDetail(1L)).thenReturn(book(1L, "热门 A", 1, 0, null));
        when(bookCatalogService.getBookDetail(2L)).thenReturn(book(2L, "热门 B", 1, 3, "A-01"));

        List<RecommendItem> result = recommendService.recommend("EXPLORE", null, null, null, 2);

        assertEquals(2, result.size());
        // 2 可借且有架位，应排在 1 前面
        assertEquals(2L, result.get(0).book().getId());
        assertEquals(1L, result.get(1).book().getId());
        // buildReason 逻辑：heat > 0 时理由为“热度较高”；heat=0 时才回落到“当前可借阅 / 馆内在架”
        assertTrue(result.get(0).reason().contains("热度较高"));
        assertTrue(result.get(1).reason().contains("热度较高"));
    }

    /**
     * 指定分类 + 关键词时，应优先使用分类召回与关键词召回的结果，不走热门兜底。
     * 验证：回查 MySQL 时过滤 status != 1 的已下架书，最终只返回上架书。
     */
    @Test
    void learn_shouldCombineCategoryAndKeywordRecall() {
        when(categoryBookRecaller.recall(10L)).thenReturn(List.of(1L, 2L));
        when(categoryBookRecaller.recallByKeyword("Redis")).thenReturn(List.of(3L));
        when(semanticBookRecallerProvider.getIfAvailable()).thenReturn(null); // RAG 关闭，降级
        when(hotBookRecaller.heatMapFor(anyList())).thenReturn(Map.of(1L, 10, 2L, 5, 3L, 0));
        when(bookCatalogService.getBookDetail(1L)).thenReturn(book(1L, "Redis 实战", 1, 2, "B-02"));
        when(bookCatalogService.getBookDetail(2L)).thenReturn(book(2L, "旧版 Redis", 0, 0, null)); // 已下架
        when(bookCatalogService.getBookDetail(3L)).thenReturn(book(3L, "Redis 入门", 1, 1, null));

        List<RecommendItem> result = recommendService.recommend("LEARN", "Redis", 10L, null, 5);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.book().getId().equals(1L)));
        assertTrue(result.stream().anyMatch(r -> r.book().getId().equals(3L)));
        assertTrue(result.stream().noneMatch(r -> r.book().getId().equals(2L)));
        // 1 可借且有架位，应排在 3 前面
        assertEquals(1L, result.get(0).book().getId());
    }

    /**
     * PERSONALIZED 意图：用户有阅读历史，个性化召回器返回候选，
     * 同时应排除 activeBookIds（当前在借未还）和已下架书。
     */
    @Test
    void personalized_shouldExcludeActiveAndDownBooks() {
        UserReadingProfile profile = new UserReadingProfile(
                1L, true,
                List.of(new UserReadingProfile.CategoryPreference(10L, "技术", 3)),
                List.of(1L, 2L),
                java.util.Set.of(1L, 2L),
                java.util.Set.of(2L) // 2 在借未还，应被排除
        );
        when(userReadingProfileService.buildProfile(1L)).thenReturn(profile);
        when(personalizedRecaller.recall(profile)).thenReturn(List.of(1L, 2L, 3L));
        // 个性化池不足时按偏好分类补书
        when(categoryBookRecaller.recall(10L)).thenReturn(List.of(3L, 4L));
        when(hotBookRecaller.heatMapFor(anyList())).thenReturn(Map.of(1L, 5, 3L, 10, 4L, 0));
        when(bookCatalogService.getBookDetail(1L)).thenReturn(book(1L, "已读过", 1, 1, null));
        when(bookCatalogService.getBookDetail(3L)).thenReturn(book(3L, "同分类新书", 1, 2, "C-03"));
        when(bookCatalogService.getBookDetail(4L)).thenReturn(book(4L, "下架书", 0, 0, null));

        List<RecommendItem> result = recommendService.recommend("PERSONALIZED", null, null, null, 5);

        // 2 在借未还应排除，4 已下架应排除，3 可借有架位排最前
        assertEquals(2, result.size()); // 1、3 都上架，但 2 被排除，4 被过滤
        assertEquals(3L, result.get(0).book().getId());
        assertTrue(result.get(0).reason().contains("根据你的借阅/购书记录推荐"));
    }

    /**
     * SIMILAR 指定种子书：应走同分类召回 + 语义召回（RAG 开启时），并排除种子书自身。
     * 本例模拟 RAG 关闭，只验证同分类召回与种子书排除。
     */
    @Test
    void similar_shouldExcludeSeedBookAndRecallSameCategory() {
        BookResponse seed = book(5L, "Spring 种子", 1, 0, null);
        seed.setCategoryId(10L);
        when(bookCatalogService.getBookDetailAdmin(5L)).thenReturn(seed);
        when(categoryBookRecaller.recall(10L)).thenReturn(List.of(5L, 6L, 7L));
        when(semanticBookRecallerProvider.getIfAvailable()).thenReturn(null);
        when(hotBookRecaller.heatMapFor(anyList())).thenReturn(Map.of(6L, 10, 7L, 5));
        when(bookCatalogService.getBookDetail(6L)).thenReturn(book(6L, "Spring Boot", 1, 1, "D-01"));
        when(bookCatalogService.getBookDetail(7L)).thenReturn(book(7L, "Spring Cloud", 1, 0, null));

        List<RecommendItem> result = recommendService.recommend("SIMILAR", null, null, 5L, 5);

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(r -> r.book().getId().equals(5L)));
        assertEquals(6L, result.get(0).book().getId()); // 可借排前
        assertTrue(result.get(0).reason().contains("与图书 #5 同类或相关"));
    }

    /**
     * limit 参数边界：大于 MAX_LIMIT（5）应被截断到 5，小于 1 应提升到 1。
     */
    @Test
    void recommend_shouldClampLimit() {
        // 无 query/categoryId，直接走热门兜底，无需 stub 分类召回
        when(hotBookRecaller.recall(50)).thenReturn(List.of(
                new HotBookStat(1L, 10),
                new HotBookStat(2L, 9),
                new HotBookStat(3L, 8),
                new HotBookStat(4L, 7),
                new HotBookStat(5L, 6),
                new HotBookStat(6L, 5)
        ));
        when(hotBookRecaller.heatMapFor(anyList())).thenReturn(Map.of(
                1L, 10, 2L, 9, 3L, 8, 4L, 7, 5L, 6, 6L, 5
        ));
        // topN 最大为 5，第 6 本书不会被回查，因此用 lenient() 避免 strict stubbing 报错
        for (long i = 1; i <= 5; i++) {
            when(bookCatalogService.getBookDetail(i)).thenReturn(book(i, "Book" + i, 1, 1, null));
        }
        lenient().when(bookCatalogService.getBookDetail(6L))
                .thenReturn(book(6L, "Book6", 1, 1, null));

        assertEquals(5, recommendService.recommend("EXPLORE", null, null, null, 10).size());
        assertEquals(1, recommendService.recommend("EXPLORE", null, null, null, 0).size());
    }

    // ===== 辅助方法：构造 BookResponse =====

    private BookResponse book(Long id, String title, int status, int borrowStock, String shelfLocation) {
        BookResponse book = new BookResponse();
        book.setId(id);
        book.setTitle(title);
        book.setStatus(status);
        book.setBorrowStock(borrowStock);
        book.setShelfLocation(shelfLocation);
        return book;
    }
}
