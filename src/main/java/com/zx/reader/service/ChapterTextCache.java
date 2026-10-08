package com.zx.reader.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zx.media.client.LiteMediaClient;
import com.zx.reader.config.ReaderProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 已解锁章节正文缓存。无权限的请求不得进入这里。
 */
@Component
public class ChapterTextCache {

    private final LiteMediaClient liteMediaClient;
    private final Cache<String, String> cache;

    public ChapterTextCache(LiteMediaClient liteMediaClient, ReaderProperties readerProperties) {
        this.liteMediaClient = liteMediaClient;
        int size = Math.max(1, readerProperties.getPreview().getChapterCacheSize());
        int minutes = Math.max(1, readerProperties.getPreview().getChapterCacheMinutes());
        this.cache = Caffeine.newBuilder()
                .maximumSize(size)
                .expireAfterWrite(Duration.ofMinutes(minutes))
                .build();
    }

    public String get(String fileId) {
        return cache.get(fileId, liteMediaClient::fetchObjectText);
    }
}
