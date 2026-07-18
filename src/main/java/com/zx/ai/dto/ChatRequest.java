package com.zx.ai.dto;

public class ChatRequest {

    /** 可选；为空时服务端生成新会话。 */
    private String sessionId;
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
