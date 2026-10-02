package com.zx.reader.service;

import com.zx.media.client.LiteMediaClient;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.ChapterContentResponse;
import com.zx.reader.dto.ChapterTocItemResponse;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户侧目录与读章 BFF（B3）。
 */
@Service
@RequiredArgsConstructor
public class EbookReaderService {

    /** 上架 */
    private static final int STATUS_ONLINE = 1;

    private final EbookBookRepository ebookBookRepository;
    private final EbookChapterRepository ebookChapterRepository;
    private final ChapterAccessService chapterAccessService;
    private final LiteMediaClient liteMediaClient;

    public List<ChapterTocItemResponse> listChapters(Long userId, Long ebookId) {
        EbookBook book = requireOnlineEbook(ebookId);
        return ebookChapterRepository.listByEbookId(book.getId()).stream()
                .map(ch -> toTocItem(userId, book, ch))
                .toList();
    }

    public ChapterContentResponse getChapterContent(Long userId, Long ebookId, Integer chapterNo) {
        EbookBook book = requireOnlineEbook(ebookId);
        EbookChapter chapter = ebookChapterRepository.findByEbookIdAndChapterNo(book.getId(), chapterNo)
                .orElseThrow(ReaderException::ebookNotFound);

        if (!chapterAccessService.canReadChapter(userId, book, chapter)) {
            // 5103：绝不调用 fetchObjectText
            throw ReaderException.previewDenied();
        }

        if (!StringUtils.hasText(chapter.getChapterFileId())) {
            throw ReaderException.chapterNotReady("章节尚未绑定媒资对象");
        }

        String text = liteMediaClient.fetchObjectText(chapter.getChapterFileId());

        ChapterContentResponse resp = new ChapterContentResponse();
        resp.setChapterNo(chapter.getChapterNo());
        resp.setTitle(chapter.getTitle());
        resp.setContent(text);
        return resp;
    }

    private ChapterTocItemResponse toTocItem(Long userId, EbookBook book, EbookChapter chapter) {
        ChapterTocItemResponse item = new ChapterTocItemResponse();
        item.setChapterNo(chapter.getChapterNo());
        item.setTitle(chapter.getTitle());
        item.setWordCount(chapter.getWordCount() == null ? 0 : chapter.getWordCount());
        item.setLocked(!chapterAccessService.canReadChapter(userId, book, chapter));
        return item;
    }

    private EbookBook requireOnlineEbook(Long ebookId) {
        EbookBook book = ebookBookRepository.findById(ebookId)
                .orElseThrow(ReaderException::ebookNotFound);
        if (book.getStatus() == null || book.getStatus() != STATUS_ONLINE) {
            throw ReaderException.ebookNotFound();
        }
        return book;
    }
}
