package com.zx.reader.service;

import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.dto.ChapterInfo;
import com.zx.media.client.dto.ChaptersResult;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 按媒资目录重建 {@code ebook_chapter}（先删后插，事务内完成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EbookTocSyncService {

    private final LiteMediaClient liteMediaClient;
    private final EbookBookRepository ebookBookRepository;
    private final EbookChapterRepository ebookChapterRepository;
    private final ReaderProperties readerProperties;

    /**
     * 按 {@code source_file_id} 同步 TOC。找不到线上书则 no-op（视频回调等）。
     *
     * @return 写入章数；未找到书返回 -1
     */
    @Transactional
    public int syncBySourceFileId(String sourceFileId) {
        if (!StringUtils.hasText(sourceFileId)) {
            return -1;
        }
        return ebookBookRepository.findBySourceFileId(sourceFileId.trim())
                .map(this::syncEbook)
                .orElse(-1);
    }

    /**
     * 按线上书主键同步 TOC（管理端手动 sync-chapters 可复用）。
     */
    @Transactional
    public int syncByEbookId(Long ebookId) {
        EbookBook book = ebookBookRepository.findById(ebookId)
                .orElseThrow(ReaderException::ebookNotFound);
        if (!StringUtils.hasText(book.getSourceFileId())) {
            throw ReaderException.chapterNotReady("线上书尚未绑定媒资 DOCUMENT");
        }
        return syncEbook(book);
    }

    private int syncEbook(EbookBook book) {
        if (book.getBookId() == null) {
            throw ReaderException.ebookNotBound();
        }
        ChaptersResult result = liteMediaClient.listChapters(book.getSourceFileId());
        List<ChapterInfo> chapters = result == null || result.chapters() == null
                ? List.of()
                : result.chapters().stream()
                .sorted(Comparator.comparingInt(ChapterInfo::chapterNo))
                .toList();

        if (chapters.isEmpty()) {
            throw ReaderException.chapterNotReady("媒资章目录为空，切章可能尚未完成: " + book.getSourceFileId());
        }

        ebookChapterRepository.deleteByEbookId(book.getId());

        int previewN = Math.max(0, readerProperties.getPreview().getDefaultChapters());
        // 若书上单独配置了 previewChapters，优先用书上的
        if (book.getPreviewChapters() != null && book.getPreviewChapters() >= 0) {
            previewN = book.getPreviewChapters();
        }

        long totalWords = 0;
        for (ChapterInfo info : chapters) {
            EbookChapter row = new EbookChapter();
            row.setEbookId(book.getId());
            row.setChapterNo(info.chapterNo());
            row.setTitle(info.title() == null || info.title().isBlank()
                    ? ("第" + info.chapterNo() + "章")
                    : info.title());
            row.setChapterFileId(info.fileId());
            row.setWordCount(Math.max(0, info.wordCount()));
            row.setIsPreviewFree(info.chapterNo() <= previewN ? 1 : 0);
            ebookChapterRepository.save(row);
            totalWords += row.getWordCount();
        }

        book.setTotalChapters(chapters.size());
        book.setWordCount(totalWords);
        ebookBookRepository.save(book);

        log.info("ebook TOC synced ebookId={} sourceFileId={} chapters={}",
                book.getId(), book.getSourceFileId(), chapters.size());
        return chapters.size();
    }
}
