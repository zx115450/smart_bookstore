package com.zx.ai.dto;

import java.util.List;

/**
 * AI 客服聊天响应：口语化文本 + 可选结构化卡片（书目 / 推荐）。
 */
public class ChatResponse {

    /** 会话 ID，前端须在后续请求中回传。 */
    private String sessionId;
    /** 模型最终回复文本。 */
    private String reply;
    /** 结构化卡片，供前端渲染；无查书/推荐结果时为空列表。 */
    private List<ChatCard> cards;

    public ChatResponse() {
    }

    public ChatResponse(String sessionId, String reply) {
        this(sessionId, reply, List.of());
    }

    public ChatResponse(String sessionId, String reply, List<ChatCard> cards) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.cards = cards == null ? List.of() : cards;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<ChatCard> getCards() {
        return cards;
    }

    public void setCards(List<ChatCard> cards) {
        this.cards = cards;
    }
}
