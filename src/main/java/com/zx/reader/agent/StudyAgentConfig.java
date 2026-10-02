package com.zx.reader.agent;

import com.zx.ai.memory.RedisStringChatMemoryRepository;
import com.zx.reader.config.ReaderProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 独立 Study Agent：与客服 {@code AiChatConfig} Prompt / Bean 隔离。
 */
@Configuration
@ConditionalOnBean(ChatModel.class)
@ConditionalOnProperty(prefix = "reader.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StudyAgentConfig {

    private static final String SYSTEM_PROMPT = """
            你是智慧书城的「学习助手」，帮助用户理解线上书、整理笔记。使用中文，简洁专业。

            【硬性约束】
            1. 涉及章文内容时，必须先调用 getChapterContent 或 summarizeChapter；工具返回无权限/失败时，明确告知用户，禁止编造未授权正文。
            2. 涉及用户笔记时，使用 getMyNotes / saveNote / rewriteNote / mergeNotes；改写与合并必须另存新笔记，不得声称已覆盖原文。
            3. 总结章节优先调用 summarizeChapter（内部有缓存），不要自己长篇编造。
            4. 不要回答借阅规则、购书客服类问题；那是另一个客服助手的职责，可建议用户去客服入口。
            5. 工具返回的 JSON 中 ok=false 时，如实转达 message，不要假装成功。
            """;

    @Bean("studyChatMemory")
    ChatMemory studyChatMemory(RedisStringChatMemoryRepository repository, ReaderProperties readerProperties) {
        int maxMessages = Math.max(2, readerProperties.getAgent().getMaxHistoryTurns() * 2);
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean("studyChatClient")
    ChatClient studyChatClient(
            ChatModel chatModel,
            ChatMemory studyChatMemory,
            StudyAgentTools studyAgentTools
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(studyChatMemory).build())
                .defaultTools(studyAgentTools)
                .build();
    }
}
