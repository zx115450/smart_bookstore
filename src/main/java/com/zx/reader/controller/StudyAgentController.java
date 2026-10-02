package com.zx.reader.controller;

import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.common.dto.ApiResponse;
import com.zx.reader.agent.StudyAgentService;
import com.zx.reader.dto.StudyChatRequest;
import com.zx.reader.dto.StudyChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Study Agent 对话入口（B5），需登录；与客服 {@code /api/ai/chat} 隔离。
 */
@RestController
@RequestMapping("/api/reader/agent")
@RequiredArgsConstructor
@ConditionalOnBean(StudyAgentService.class)
@ConditionalOnProperty(prefix = "reader.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StudyAgentController {

    private final StudyAgentService studyAgentService;

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/chat")
    public ApiResponse<StudyChatResponse> chat(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody StudyChatRequest request
    ) {
        return ApiResponse.ok(studyAgentService.chat(principal, request));
    }
}
