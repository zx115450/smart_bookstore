-- 为种子图书挂短篇线上试读（正文走 MockLiteMediaClient / demo-e{id}-c{n}，不落库）
-- 前 2 章试看免费，第 3 章需借阅或购买解锁

INSERT INTO ebook_book (
  book_id, title, author, format, status, preview_mode, preview_chapters,
  total_chapters, word_count, source_file_id
)
SELECT
  b.id,
  CONCAT(b.title, '（线上试读）'),
  b.author,
  'MARKDOWN',
  1,
  'CHAPTER',
  2,
  3,
  360,
  CONCAT('demo-src-', b.id)
FROM book b
WHERE b.title IN (
  'Java 核心技术',
  'Spring Boot 实战',
  'Redis 设计与实现',
  '红楼梦',
  '三国演义'
)
  AND b.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM ebook_book e WHERE e.book_id = b.id
  );

INSERT INTO ebook_chapter (ebook_id, chapter_no, title, chapter_file_id, word_count, is_preview_free)
SELECT e.id, 1, '开篇导读', CONCAT('demo-e', e.id, '-c1'), 80, 1
FROM ebook_book e
WHERE e.source_file_id LIKE 'demo-src-%'
  AND NOT EXISTS (
    SELECT 1 FROM ebook_chapter c WHERE c.ebook_id = e.id AND c.chapter_no = 1
  );

INSERT INTO ebook_chapter (ebook_id, chapter_no, title, chapter_file_id, word_count, is_preview_free)
SELECT e.id, 2, '要点速览', CONCAT('demo-e', e.id, '-c2'), 90, 1
FROM ebook_book e
WHERE e.source_file_id LIKE 'demo-src-%'
  AND NOT EXISTS (
    SELECT 1 FROM ebook_chapter c WHERE c.ebook_id = e.id AND c.chapter_no = 2
  );

INSERT INTO ebook_chapter (ebook_id, chapter_no, title, chapter_file_id, word_count, is_preview_free)
SELECT e.id, 3, '进阶片段', CONCAT('demo-e', e.id, '-c3'), 100, 0
FROM ebook_book e
WHERE e.source_file_id LIKE 'demo-src-%'
  AND NOT EXISTS (
    SELECT 1 FROM ebook_chapter c WHERE c.ebook_id = e.id AND c.chapter_no = 3
  );
