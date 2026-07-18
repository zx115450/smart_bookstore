package com.zx.ai.dto;

import java.util.List;

public class ChatResponse {

    private String sessionId;
    private String reply;
    /** 结构化卡片，供前端渲染；无查书结果时为空列表 */
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
