package com.zx.reader.service;

import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 试看 / 解锁判定（书城侧）。无权限时禁止调用媒资拉正文。
 */
@Service
@RequiredArgsConstructor
public class ChapterAccessService {

    private final MediaAccessService mediaAccessService;

    /**
     * {@code is_preview_free=1} 可读；否则需借阅中（仅 BORROWED，不含逾期）、已购，或 ADMIN。
     */
    public boolean canReadChapter(Long userId, EbookBook ebook, EbookChapter chapter) {
        return canReadChapter(userId, ebook, chapter, null);
    }

    public boolean canReadChapter(Long userId, EbookBook ebook, EbookChapter chapter, Collection<String> roles) {
        if (chapter != null && chapter.getIsPreviewFree() != null && chapter.getIsPreviewFree() == 1) {
            return true;
        }
        return hasUnlockedAccess(userId, ebook, roles);
    }

    /**
     * 用户对该实体书借阅中或已购（若绑了 {@code book_id}）。ADMIN 与视频解锁同一放行规则。
     */
    boolean hasUnlockedAccess(Long userId, EbookBook ebook, Collection<String> roles) {
        Long bookId = ebook == null ? null : ebook.getBookId();
        return mediaAccessService.canWatchFullMedia(userId, bookId, roles);
    }
}
