package com.zx.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.reader.entity.EbookChapter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EbookChapterMapper extends BaseMapper<EbookChapter> {

    @Insert("""
            <script>
            INSERT INTO ebook_chapter
              (ebook_id, chapter_no, title, chapter_file_id, word_count, is_preview_free, created_at, updated_at)
            VALUES
            <foreach collection='list' item='c' separator=','>
              (#{c.ebookId}, #{c.chapterNo}, #{c.title}, #{c.chapterFileId},
               #{c.wordCount}, #{c.isPreviewFree}, #{c.createdAt}, #{c.updatedAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<EbookChapter> list);
}
