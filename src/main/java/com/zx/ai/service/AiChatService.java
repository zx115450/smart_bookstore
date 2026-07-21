package com.zx.ai.service;

import com.zx.ai.dto.ChatCard;
import com.zx.ai.dto.ChatRequest;
import com.zx.ai.dto.ChatResponse;
import com.zx.ai.exception.AiException;
import com.zx.ai.support.AiUserContext;
import com.zx.ai.support.ChatCardCollector;
import com.zx.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * AI 对话应用服务：校验入参、构造会话键、注入鉴权上下文，再调用 {@link ChatClient}。
 * <p>
 * 不直接访问业务库；事实查询由模型通过 Tool 完成。ThreadLocal 上下文务必在 finally 中清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiChatService {

    private final ChatClient aiChatClient;

    /**
     * 执行一轮对话。
     * <ol>
     *   <li>生成或复用 {@code sessionId}（返回给前端，多轮必回传）</li>
     *   <li>拼 {@code conversationId}：登录 {@code user:{userId}:{sessionId}}，匿名 {@code anon:{sessionId}}</li>
     *   <li>写入 {@link AiUserContext} 供个人类 Tool 取 userId（禁止让模型传 userId）</li>
     *   <li>调用 ChatClient；从 {@link ChatCardCollector} 取出本轮卡片</li>
     * </ol>
     */
    public ChatResponse chat(ChatRequest request, AuthPrincipal principal) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw AiException.badRequest("message 不能为空");
        }
        String message = request.getMessage().trim();
        if (message.length() > 2000) {
            throw AiException.badRequest("message 过长，请控制在 2000 字以内");
        }

        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString().replace("-", "");

        // 登录用户会话与匿名会话隔离；conversationId 由 MessageChatMemoryAdvisor 读写 Redis
        String conversationId = (principal != null && principal.userId() != null)
                ? "user:" + principal.userId() + ":" + sessionId
                : "anon:" + sessionId;

        AiUserContext.setUserId(principal == null ? null : principal.userId());
        ChatCardCollector.clear();
        try {
            String reply = aiChatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            if (!StringUtils.hasText(reply)) {
                throw AiException.unavailable("模型返回为空");
            }

            List<ChatCard> cards = ChatCardCollector.drain();
            log.info("ai chat ok sessionId={} login={} cards={}", sessionId,
                    principal != null, cards.size());
            return new ChatResponse(sessionId, reply.trim(), cards);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("ai chat failed sessionId={}", sessionId, e);
            throw AiException.unavailable("AI 服务暂时不可用，请稍后重试");
        } finally {
            AiUserContext.clear();
            ChatCardCollector.clear();
        }
    }
}
