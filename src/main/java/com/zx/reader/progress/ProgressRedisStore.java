package com.zx.reader.progress;

import com.zx.reader.config.ReaderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 进度热缓存：只维护 chapterId / charOffset / lastActive + dirty / pendingFlush。
 */
@Component
@RequiredArgsConstructor
public class ProgressRedisStore {

    static final String FIELD_CHAPTER_ID = "chapterId";
    static final String FIELD_CHAR_OFFSET = "charOffset";
    static final String FIELD_LAST_ACTIVE = "lastActive";
    static final String FIELD_DIRTY = "dirty";
    static final String FIELD_PENDING_FLUSH = "pendingFlush";

    private final StringRedisTemplate redis;
    private final ReaderProperties readerProperties;

    public String key(Long userId, Long ebookId) {
        return "reader:progress:" + userId + ":" + ebookId;
    }

    public Optional<ProgressHotState> get(Long userId, Long ebookId) {
        Map<Object, Object> map = redis.opsForHash().entries(key(userId, ebookId));
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        Long chapterId = parseLong(map.get(FIELD_CHAPTER_ID));
        Integer charOffset = parseInt(map.get(FIELD_CHAR_OFFSET));
        if (chapterId == null || charOffset == null) {
            return Optional.empty();
        }
        Long lastActive = parseLong(map.get(FIELD_LAST_ACTIVE));
        boolean dirty = "1".equals(String.valueOf(map.get(FIELD_DIRTY)));
        boolean pending = "1".equals(String.valueOf(map.get(FIELD_PENDING_FLUSH)));
        return Optional.of(new ProgressHotState(
                chapterId, charOffset, lastActive == null ? 0L : lastActive, dirty, pending));
    }

    /**
     * 更新位置与 lastActive，标 dirty，并置 pendingFlush=1。
     *
     * @return true 表示此前无 pendingFlush，调用方应投递延迟消息（固定一个在途到期点）
     */
    public boolean touchAndMarkDirty(Long userId, Long ebookId, Long chapterId, int charOffset, long nowMs) {
        String k = key(userId, ebookId);
        Object oldPending = redis.opsForHash().get(k, FIELD_PENDING_FLUSH);
        boolean needSchedule = !"1".equals(String.valueOf(oldPending));

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_CHAPTER_ID, String.valueOf(chapterId));
        fields.put(FIELD_CHAR_OFFSET, String.valueOf(Math.max(0, charOffset)));
        fields.put(FIELD_LAST_ACTIVE, String.valueOf(nowMs));
        fields.put(FIELD_DIRTY, "1");
        fields.put(FIELD_PENDING_FLUSH, "1");
        redis.opsForHash().putAll(k, fields);
        redis.expire(k, ttl());
        return needSchedule;
    }

    /** 落库成功：清 dirty / pending。 */
    public void markClean(Long userId, Long ebookId) {
        String k = key(userId, ebookId);
        redis.opsForHash().put(k, FIELD_DIRTY, "0");
        redis.opsForHash().put(k, FIELD_PENDING_FLUSH, "0");
        redis.expire(k, ttl());
    }

    /** 再延后：保持 dirty，确保 pendingFlush=1。 */
    public void markPendingFlush(Long userId, Long ebookId) {
        String k = key(userId, ebookId);
        redis.opsForHash().put(k, FIELD_PENDING_FLUSH, "1");
        redis.opsForHash().put(k, FIELD_DIRTY, "1");
        redis.expire(k, ttl());
    }

    private Duration ttl() {
        long hours = Math.max(1L, readerProperties.getProgress().getRedisTtlHours());
        return Duration.ofHours(hours);
    }

    private static Long parseLong(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
