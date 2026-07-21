package com.zx.ai.config;

import com.zx.ai.memory.RedisStringChatMemoryRepository;
import com.zx.ai.tool.BookDetailTool;
import com.zx.ai.tool.BookRecommendTool;
import com.zx.ai.tool.BookSearchTool;
import com.zx.ai.tool.BorrowTool;
import com.zx.ai.tool.FaqTool;
import com.zx.ai.tool.UserReadingProfileTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话编排装配：System Prompt + ChatMemory Advisor + 业务 Tools。
 * <p>
 * 仅在 {@code ai.enabled=true}（默认）时生效。模型本身由
 * {@code spring-ai-starter-model-openai} 自动提供 {@link ChatClient.Builder}。
 */
@Configuration
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiChatConfig {

    /**
     * 系统提示：强制「事实走 Tool、禁止编造」。
     * 与各 {@code @Tool} 的 description 互补，约束模型何时必须调工具。
     */
    private static final String SYSTEM_PROMPT = """
            你是智慧书城的客服助手，回答简洁友好，使用中文。

            【查书类】涉及「有没有某本书」「书在哪/几楼几层」「库存」时，必须先调用 searchBooks 或 getBookDetail，只能根据工具返回的 JSON 回答，禁止编造书名、架位或库存。
            若工具返回 found=false 或 books 为空，明确告知馆内暂无或已下架，可建议换关键词。
            回答架位时优先使用字段 shelfLocation；提及借阅是否可借时参考 borrowStock。

            【规则类】涉及业务规则与流程（怎么借书/还书、待取书架位、借阅到期/逾期、连续签到奖励、怎么预约自习室、购书下单与自动取消、优惠券怎么用）时，必须先调用 searchFaq，只能根据工具返回的 answer 组织回复，禁止编造规则细节、接口路径或时间数值。
            若 searchFaq 返回 found=false，明确告知暂无该规则，建议换种问法或联系人工客服。

            【个人借阅类】涉及「我借的书」「我的待取书」「我有什么待还」「我的借阅状态」等当前用户个人借阅时，必须先调用 getMyBorrowOrders，只能根据工具返回的 orders 回答，禁止编造订单。
            若 getMyBorrowOrders 返回未登录提示，必须明确告知用户「请先登录后再查询个人借阅」，不要假装查到了数据。
            回答待取书（APPLIED）时给出 shelfLocation 架位；回答借阅中（BORROWED/OVERDUE）时给出 dueAt 到期时间；逾期（OVERDUE）时提醒尽快归还。

            【推荐类】涉及「有什么书推荐」「想学 XX 推荐几本」「文学区有什么」「推荐一本小说」等推荐请求时，必须先调用 recommendBooks，只能根据工具返回的 books 回答，禁止编造推荐书名。
            intent 取值：EXPLORE(随便看看/有什么书)、LEARN(想学某主题)、SIMILAR(找类似的，可传 seedBookId)、RELAX(休闲读物)、PERSONALIZED(根据我借过的/个性化推荐)。
            「根据我借过的推荐」「和刚还的书类似的」「个性化推荐」等个性化需求：先调用 getUserReadingProfile 了解用户画像（未登录则提示登录），再调用 recommendBooks(intent=PERSONALIZED) 获取推荐；未登录时 recommendBooks 会返回需登录提示，必须如实转达。
            SIMILAR 场景若用户指定了某本书，传 seedBookId 基于此书找同类/相似。
            推荐回复须包含每本的 title 与 recommendReason，并尽量给出 shelfLocation 与是否可借（borrowStock>0）。

            【超出能力】其他未接入的能力，如实说明，不要编造业务数据。
            """;

    /**
     * 滑动窗口记忆：最多保留 {@code maxHistoryTurns} 轮对话。
     * 乘 2 是因为一轮通常包含 user + assistant 两条 {@link org.springframework.ai.chat.messages.Message}。
     */
    @Bean
    ChatMemory aiChatMemory(RedisStringChatMemoryRepository repository, AiProperties aiProperties) {
        int maxMessages = Math.max(2, aiProperties.getMaxHistoryTurns() * 2);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * 业务侧统一入口 ChatClient：挂上系统提示、会话记忆 Advisor、全部业务 Tool。
     * Tool Calling 循环由 Spring AI 2.0 默认的 ToolCallingAdvisor 处理。
     */
    @Bean
    ChatClient aiChatClient(
            ChatClient.Builder builder,
            ChatMemory aiChatMemory,
            BookSearchTool bookSearchTool,
            BookDetailTool bookDetailTool,
            FaqTool faqTool,
            BorrowTool borrowTool,
            BookRecommendTool bookRecommendTool,
            UserReadingProfileTool userReadingProfileTool
    ) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(aiChatMemory).build())
                .defaultTools(bookSearchTool, bookDetailTool, faqTool, borrowTool,
                        bookRecommendTool, userReadingProfileTool)
                .build();
    }
}
