package com.zx.ai.dto;

/**
 * AI 客服聊天请求体。
 */
public class ChatRequest {

    /**
     * 会话 ID；可选。为空时服务端生成新 ID，多轮对话须原样回传以保持记忆。
     */
    private String sessionId;

    /** 用户本轮输入，必填，最长 2000 字。 */
    private String message;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
