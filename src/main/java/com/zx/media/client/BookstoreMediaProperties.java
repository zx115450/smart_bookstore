package com.zx.media.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 书城媒资业务配置（与 {@link LiteMediaProperties} 并列，前缀 {@code bookstore.media}）。
 */
@ConfigurationProperties(prefix = "bookstore.media")
public class BookstoreMediaProperties {

    /** 绑定视频时未传 previewSeconds 的默认试看秒数。 */
    private int defaultPreviewSeconds = 300;

    public int getDefaultPreviewSeconds() {
        return defaultPreviewSeconds;
    }

    public void setDefaultPreviewSeconds(int defaultPreviewSeconds) {
        this.defaultPreviewSeconds = defaultPreviewSeconds;
    }
}
