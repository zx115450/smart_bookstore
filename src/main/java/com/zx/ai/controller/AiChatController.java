package com.zx.ai.controller;

import com.zx.ai.dto.ChatRequest;
import com.zx.ai.dto.ChatResponse;
import com.zx.ai.service.AiChatService;
import com.zx.common.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服对话 HTTP 入口。
 * <p>
 * {@code POST /api/ai/chat} 在 Security 中为 {@code permitAll}：允许匿名聊天；
 * 若请求带 JWT，过滤器仍会注入 {@link AuthPrincipal}，从而启用个人借阅 / 个性化推荐等 Tool。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 发送一轮用户消息，返回口语化回复与可选书目/推荐卡片。
     *
     * @param request   sessionId（可选）+ message（必填）
     * @param principal 登录用户；匿名时为 null
     */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestAttribute(name = AuthAttributes.AUTH_USER, required = false) AuthPrincipal principal
    ) {
        return ApiResponse.ok(aiChatService.chat(request, principal));
    }
}
