package com.zx.reader.service;

import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 试看 / 解锁判定（书城侧）。无权限时禁止调用媒资拉正文。
 */
@Service
@RequiredArgsConstructor
public class ChapterAccessService {

    private final MediaAccessService mediaAccessService;

    /**
     * {@code is_preview_free=1} 可读；否则需借阅中（仅 BORROWED，不含逾期）或已购。
     */
    public boolean canReadChapter(Long userId, EbookBook ebook, EbookChapter chapter) {
        if (chapter != null && chapter.getIsPreviewFree() != null && chapter.getIsPreviewFree() == 1) {
            return true;
        }
        return hasUnlockedAccess(userId, ebook);
    }

    /**
     * 用户对该实体书借阅中或已购（若绑了 {@code book_id}）。
     */
    boolean hasUnlockedAccess(Long userId, EbookBook ebook) {
        if (userId == null || ebook == null || ebook.getBookId() == null) {
            return false;
        }
        return mediaAccessService.canWatchFullMedia(userId, ebook.getBookId());
    }
}
