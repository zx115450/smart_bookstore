package com.zx.reader.agent;

import com.zx.auth.security.AuthPrincipal;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.StudyChatRequest;
import com.zx.reader.dto.StudyChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Study Agent 对话入口：限流 → 会话键 study:user:{id}:{sid} → studyChatClient。
 */
@Slf4j
@Service
@ConditionalOnBean(name = "studyChatClient")
@ConditionalOnProperty(prefix = "reader.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StudyAgentService {

    private final ChatClient studyChatClient;
    private final StudyAgentRateLimiter rateLimiter;
    private final ReaderProperties readerProperties;

    public StudyAgentService(
            @Qualifier("studyChatClient") ChatClient studyChatClient,
            StudyAgentRateLimiter rateLimiter,
            ReaderProperties readerProperties
    ) {
        this.studyChatClient = studyChatClient;
        this.rateLimiter = rateLimiter;
        this.readerProperties = readerProperties;
    }

    public StudyChatResponse chat(AuthPrincipal principal, StudyChatRequest request) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalArgumentException("请先登录后再使用学习助手");
        }
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new IllegalArgumentException("message 不能为空");
        }
        String message = request.getMessage().trim();
        int maxChars = Math.max(100, readerProperties.getAgent().getMaxInputChars());
        if (message.length() > maxChars) {
            throw new IllegalArgumentException("message 过长，请控制在 " + maxChars + " 字以内");
        }

        Long userId = principal.userId();
        rateLimiter.checkAndIncrement(userId);

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        String conversationId = "study:user:" + userId + ":" + sessionId;

        StudyAgentContext.set(userId, request.getEbookId());
        try {
            String reply = studyChatClient.prompt()
                    .user(buildUserPayload(request.getEbookId(), message))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            if (!StringUtils.hasText(reply)) {
                throw new IllegalStateException("模型返回为空");
            }
            log.info("study chat ok userId={} sessionId={} ebookId={}",
                    userId, sessionId, request.getEbookId());
            return new StudyChatResponse(sessionId, reply.trim());
        } catch (ReaderException e) {
            throw e;
        } catch (Exception e) {
            log.error("study chat failed userId={} sessionId={}", userId, sessionId, e);
            throw new IllegalStateException("学习助手暂时不可用，请稍后重试");
        } finally {
            StudyAgentContext.clear();
        }
    }

    private static String buildUserPayload(Long ebookId, String message) {
        if (ebookId == null) {
            return message;
        }
        return "【当前电子书 ebookId=" + ebookId + "】\n" + message;
    }
}
