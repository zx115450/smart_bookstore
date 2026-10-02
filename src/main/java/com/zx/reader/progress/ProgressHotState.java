package com.zx.reader.progress;

/**
 * Redis 中的阅读进度热数据（合并写缓冲）。
 */
public record ProgressHotState(
        Long chapterId,
        Integer charOffset,
        long lastActiveMs,
        boolean dirty,
        boolean pendingFlush
) {
}
