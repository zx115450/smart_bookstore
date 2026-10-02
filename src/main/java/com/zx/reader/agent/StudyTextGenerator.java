package com.zx.reader.agent;

import com.zx.reader.config.ReaderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 无 Tool 的一次性文本生成（总结 / 改写 / 合并），避免与 studyChatClient Tool 循环嵌套。
 */
@Slf4j
@Component
@ConditionalOnBean(ChatModel.class)
@RequiredArgsConstructor
public class StudyTextGenerator {

    private final ChatModel chatModel;
    private final ReaderProperties readerProperties;

    public String generate(String systemHint, String userContent) {
        String truncated = truncate(userContent);
        String reply = ChatClient.create(chatModel)
                .prompt()
                .system(systemHint == null ? "你是学习助手，使用中文，基于给定材料作答，禁止编造未提供的内容。" : systemHint)
                .user(truncated)
                .call()
                .content();
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("模型返回为空");
        }
        return reply.trim();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int max = Math.max(500, readerProperties.getAgent().getMaxInputChars());
        if (text.length() <= max) {
            return text;
        }
        log.info("study input truncated from {} to {}", text.length(), max);
        return text.substring(0, max) + "\n\n[内容已截断]";
    }
}
