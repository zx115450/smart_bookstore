package com.zx.reader.service;

import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.ReadingProgressResponse;
import com.zx.reader.dto.UpdateProgressRequest;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.entity.EbookReadingProgress;
import com.zx.reader.progress.ProgressFlushMessage;
import com.zx.reader.progress.ProgressHotState;
import com.zx.reader.progress.ProgressRedisStore;
import com.zx.reader.progress.ReaderProgressMqProducer;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.EbookReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 阅读进度：Redis 热写 + RabbitMQ 延迟合并落库。
 * <ul>
 *   <li>charOffset（类比 moment）：合并写，停更后刷 MySQL</li>
 *   <li>换章：即时写库（类比首次 finished 即时落库）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingProgressService {

    private static final int STATUS_ONLINE = 1;

    private final EbookBookRepository ebookBookRepository;
    private final EbookChapterRepository ebookChapterRepository;
    private final EbookReadingProgressRepository progressRepository;
    private final ChapterAccessService chapterAccessService;
    private final ProgressRedisStore progressRedisStore;
    private final ReaderProgressMqProducer progressMqProducer;
    private final ReaderProperties readerProperties;

    public ReadingProgressResponse getProgress(Long userId, Long ebookId) {
        requireOnlineEbook(ebookId);
        return progressRedisStore.get(userId, ebookId)
                .map(hot -> toResponse(ebookId, hot.chapterId(), hot.charOffset(),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(hot.lastActiveMs()), ZoneId.systemDefault())))
                .orElseGet(() -> progressRepository.findByUserAndEbook(userId, ebookId)
                        .map(p -> toResponse(ebookId, p.getChapterId(), p.getCharOffset(), p.getUpdatedAt()))
                        .orElse(null));
    }

    public ReadingProgressResponse updateProgress(Long userId, Long ebookId, UpdateProgressRequest req) {
        if (req == null || req.getCharOffset() == null) {
            throw new IllegalArgumentException("charOffset 必填");
        }
        EbookBook book = requireOnlineEbook(ebookId);
        EbookChapter chapter = resolveChapter(ebookId, req);
        if (!chapterAccessService.canReadChapter(userId, book, chapter)) {
            throw ReaderException.previewDenied();
        }

        int offset = Math.max(0, req.getCharOffset());
        Long newChapterId = chapter.getId();
        long now = System.currentTimeMillis();

        Long previousChapterId = progressRedisStore.get(userId, ebookId)
                .map(ProgressHotState::chapterId)
                .orElseGet(() -> progressRepository.findByUserAndEbook(userId, ebookId)
                        .map(EbookReadingProgress::getChapterId)
                        .orElse(null));

        boolean chapterChanged = previousChapterId != null && !previousChapterId.equals(newChapterId);
        boolean firstProgress = previousChapterId == null;

        if (!readerProperties.getProgress().isCoalesceEnabled()) {
            persist(userId, ebookId, newChapterId, offset);
            progressRedisStore.touchAndMarkDirty(userId, ebookId, newChapterId, offset, now);
            progressRedisStore.markClean(userId, ebookId);
            return toResponse(ebookId, newChapterId, offset, LocalDateTime.now());
        }

        // 换章 / 首次：即时写库（对应视频「首次 finished 即时落库」）
        if (chapterChanged || firstProgress) {
            persist(userId, ebookId, newChapterId, offset);
            progressRedisStore.touchAndMarkDirty(userId, ebookId, newChapterId, offset, now);
            progressRedisStore.markClean(userId, ebookId);
            return toResponse(ebookId, newChapterId, offset, LocalDateTime.now());
        }

        // 同章偏移：只热写 Redis；若尚无在途延迟消息则投递一条
        boolean needSchedule = progressRedisStore.touchAndMarkDirty(
                userId, ebookId, newChapterId, offset, now);
        if (needSchedule) {
            progressMqProducer.scheduleFlush(userId, ebookId);
        }
        return toResponse(ebookId, newChapterId, offset,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()));
    }

    /**
     * 延迟消息到期：若仍活跃则再延后；否则 Redis → MySQL。
     */
    public void onFlushDue(ProgressFlushMessage message) {
        if (message == null || message.getUserId() == null || message.getEbookId() == null) {
            return;
        }
        Long userId = message.getUserId();
        Long ebookId = message.getEbookId();
        ProgressHotState hot = progressRedisStore.get(userId, ebookId).orElse(null);
        if (hot == null || !hot.dirty()) {
            progressRedisStore.markClean(userId, ebookId);
            return;
        }

        long now = System.currentTimeMillis();
        long idleMs = Math.max(0L, readerProperties.getProgress().getIdleMs());
        if (now - hot.lastActiveMs() < idleMs) {
            // 仍在阅读：再延后（滑动窗口）
            progressRedisStore.markPendingFlush(userId, ebookId);
            progressMqProducer.scheduleFlush(userId, ebookId);
            log.debug("progress flush deferred userId={} ebookId={} idleLeftMs={}",
                    userId, ebookId, idleMs - (now - hot.lastActiveMs()));
            return;
        }

        persist(userId, ebookId, hot.chapterId(), hot.charOffset());
        progressRedisStore.markClean(userId, ebookId);
        log.info("progress flushed userId={} ebookId={} chapterId={} offset={}",
                userId, ebookId, hot.chapterId(), hot.charOffset());
    }

    @Transactional
    protected void persist(Long userId, Long ebookId, Long chapterId, int charOffset) {
        EbookReadingProgress row = progressRepository.findByUserAndEbook(userId, ebookId)
                .orElseGet(() -> {
                    EbookReadingProgress p = new EbookReadingProgress();
                    p.setUserId(userId);
                    p.setEbookId(ebookId);
                    return p;
                });
        row.setChapterId(chapterId);
        row.setCharOffset(charOffset);
        progressRepository.save(row);
    }

    private EbookChapter resolveChapter(Long ebookId, UpdateProgressRequest req) {
        if (req.getChapterId() != null) {
            EbookChapter ch = ebookChapterRepository.findById(req.getChapterId())
                    .orElseThrow(ReaderException::ebookNotFound);
            if (!ebookId.equals(ch.getEbookId())) {
                throw ReaderException.ebookNotFound();
            }
            return ch;
        }
        if (req.getChapterNo() != null) {
            return ebookChapterRepository.findByEbookIdAndChapterNo(ebookId, req.getChapterNo())
                    .orElseThrow(ReaderException::ebookNotFound);
        }
        throw new IllegalArgumentException("chapterId 或 chapterNo 必填");
    }

    private EbookBook requireOnlineEbook(Long ebookId) {
        EbookBook book = ebookBookRepository.findById(ebookId)
                .orElseThrow(ReaderException::ebookNotFound);
        if (book.getStatus() == null || book.getStatus() != STATUS_ONLINE) {
            throw ReaderException.ebookNotFound();
        }
        return book;
    }

    private ReadingProgressResponse toResponse(Long ebookId, Long chapterId, Integer charOffset,
                                               LocalDateTime updatedAt) {
        ReadingProgressResponse resp = new ReadingProgressResponse();
        resp.setEbookId(ebookId);
        resp.setChapterId(chapterId);
        resp.setCharOffset(charOffset == null ? 0 : charOffset);
        resp.setUpdatedAt(updatedAt);
        if (chapterId != null) {
            ebookChapterRepository.findById(chapterId)
                    .ifPresent(ch -> resp.setChapterNo(ch.getChapterNo()));
        }
        return resp;
    }
}
