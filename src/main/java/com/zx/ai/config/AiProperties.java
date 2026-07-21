package com.zx.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 业务侧配置，对应 {@code application.yaml} 中 {@code ai.*} 前缀。
 * <p>
 * 与 {@code spring.ai.*}（模型 / 向量库自动装配）分离：本类只管会话窗口、限流、开关等业务参数。
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 总开关；false 时 AI Controller / ChatClient 相关 Bean 不创建。 */
    private boolean enabled = true;
    /** 对话模型名（透传给 spring.ai.openai.chat.options.model 的占位来源之一）。 */
    private String chatModel = "deepseek-chat";
    /** 采样温度，客服场景宜偏低以减少幻觉。 */
    private double temperature = 0.3;
    /**
     * 多轮对话保留的「轮」数（一轮 ≈ user + assistant 两条消息）。
     * ChatMemory 的 maxMessages 按 {@code maxHistoryTurns * 2} 计算。
     */
    private int maxHistoryTurns = 6;
    private RateLimit rateLimit = new RateLimit();
    private Session session = new Session();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxHistoryTurns() {
        return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(int maxHistoryTurns) {
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /** 每用户每小时调用上限（配置项；限流实现可按需接入）。 */
    public static class RateLimit {
        private int perUserHourly = 30;

        public int getPerUserHourly() {
            return perUserHourly;
        }

        public void setPerUserHourly(int perUserHourly) {
            this.perUserHourly = perUserHourly;
        }
    }

    /** 会话记忆在 Redis 中的 TTL。 */
    public static class Session {
        /** 会话空闲过期分钟数，写入 ChatMemory 的 Redis Key 过期时间。 */
        private int ttlMinutes = 30;

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}
