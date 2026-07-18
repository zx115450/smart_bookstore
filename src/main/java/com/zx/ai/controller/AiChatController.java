package com.zx.ai.controller;

import com.zx.ai.dto.ChatRequest;
import com.zx.ai.dto.ChatResponse;
import com.zx.ai.service.AiChatService;
import com.zx.auth.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestAttribute(name = AuthAttributes.AUTH_USER, required = false) AuthPrincipal principal
    ) {
        return ApiResponse.ok(aiChatService.chat(request, principal));
    }
}
