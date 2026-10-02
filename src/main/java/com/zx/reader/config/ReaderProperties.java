package com.zx.reader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线上阅读配置。
 */
@ConfigurationProperties(prefix = "reader")
public class ReaderProperties {

    private boolean enabled = true;

    private final Preview preview = new Preview();
    private final Progress progress = new Progress();
    private final Notes notes = new Notes();
    private final Agent agent = new Agent();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Preview getPreview() {
        return preview;
    }

    public Progress getProgress() {
        return progress;
    }

    public Notes getNotes() {
        return notes;
    }

    public Agent getAgent() {
        return agent;
    }

    public static class Preview {
        /** 导入同步 TOC 时前 N 章标记试看免费。 */
        private int defaultChapters = 2;

        public int getDefaultChapters() {
            return defaultChapters;
        }

        public void setDefaultChapters(int defaultChapters) {
            this.defaultChapters = defaultChapters;
        }
    }

    /**
     * 阅读进度：Redis 热写 + RabbitMQ 延迟合并落库。
     * <p>
     * 换章视为「首次里程碑」即时写库（对应视频侧首次 finished 即时落库）。
     */
    public static class Progress {
        /** false 时 PUT 直写 MySQL（单测 / 无 MQ 环境）。 */
        private boolean coalesceEnabled = true;
        /** 延迟消息 x-delay（毫秒）。 */
        private long delayMs = 10_000L;
        /**
         * 到期时若 {@code now - lastActive < idleMs} 则再延后，否则落库。
         * 即「停更超过 idle 才刷库」。
         */
        private long idleMs = 5_000L;
        /** Redis key TTL。 */
        private long redisTtlHours = 168L;

        public boolean isCoalesceEnabled() {
            return coalesceEnabled;
        }

        public void setCoalesceEnabled(boolean coalesceEnabled) {
            this.coalesceEnabled = coalesceEnabled;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public long getIdleMs() {
            return idleMs;
        }

        public void setIdleMs(long idleMs) {
            this.idleMs = idleMs;
        }

        public long getRedisTtlHours() {
            return redisTtlHours;
        }

        public void setRedisTtlHours(long redisTtlHours) {
            this.redisTtlHours = redisTtlHours;
        }
    }

    public static class Notes {
        /** 锁定章允许的最大划线长度；超出拒绝（防泄文）。 */
        private int maxQuoteOnLocked = 80;

        public int getMaxQuoteOnLocked() {
            return maxQuoteOnLocked;
        }

        public void setMaxQuoteOnLocked(int maxQuoteOnLocked) {
            this.maxQuoteOnLocked = maxQuoteOnLocked;
        }
    }

    /** Study Agent（B5）。 */
    public static class Agent {
        private boolean enabled = true;
        private int maxInputChars = 12_000;
        /** 每用户每小时调用上限，超限 5301。 */
        private int rateLimitPerUserHourly = 20;
        private int maxHistoryTurns = 8;
        /** 写入总结缓存 key 的模型版本标签。 */
        private String modelVersion = "deepseek-chat";
        private int sessionTtlMinutes = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }

        public int getRateLimitPerUserHourly() {
            return rateLimitPerUserHourly;
        }

        public void setRateLimitPerUserHourly(int rateLimitPerUserHourly) {
            this.rateLimitPerUserHourly = rateLimitPerUserHourly;
        }

        public int getMaxHistoryTurns() {
            return maxHistoryTurns;
        }

        public void setMaxHistoryTurns(int maxHistoryTurns) {
            this.maxHistoryTurns = maxHistoryTurns;
        }

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public int getSessionTtlMinutes() {
            return sessionTtlMinutes;
        }

        public void setSessionTtlMinutes(int sessionTtlMinutes) {
            this.sessionTtlMinutes = sessionTtlMinutes;
        }
    }
}
