package com.zx.reader.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.mapper.EbookChapterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EbookChapterRepository {

    private final EbookChapterMapper mapper;

    public Optional<EbookChapter> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<EbookChapter> findByEbookIdAndChapterNo(Long ebookId, Integer chapterNo) {
        if (ebookId == null || chapterNo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<EbookChapter>lambdaQuery()
                        .eq(EbookChapter::getEbookId, ebookId)
                        .eq(EbookChapter::getChapterNo, chapterNo)
        ));
    }

    public List<EbookChapter> listByEbookId(Long ebookId) {
        if (ebookId == null) {
            return Collections.emptyList();
        }
        return mapper.selectList(
                Wrappers.<EbookChapter>lambdaQuery()
                        .eq(EbookChapter::getEbookId, ebookId)
                        .orderByAsc(EbookChapter::getChapterNo)
        );
    }

    public int deleteByEbookId(Long ebookId) {
        if (ebookId == null) {
            return 0;
        }
        return mapper.delete(
                Wrappers.<EbookChapter>lambdaQuery()
                        .eq(EbookChapter::getEbookId, ebookId)
        );
    }

    public EbookChapter save(EbookChapter chapter) {
        LocalDateTime now = LocalDateTime.now();
        if (chapter.getId() == null) {
            if (chapter.getCreatedAt() == null) {
                chapter.setCreatedAt(now);
            }
            chapter.setUpdatedAt(now);
            mapper.insert(chapter);
            return chapter;
        }
        chapter.setUpdatedAt(now);
        mapper.updateById(chapter);
        return chapter;
    }
}
