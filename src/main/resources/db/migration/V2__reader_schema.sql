-- Flyway V2：线上阅读与学习 Agent 目录表
-- 硬性约束：ebook_chapter 禁止 content_text / content / body 等正文列；
-- chapter_file_id、source_file_id 均为 VARCHAR，不跨库 FK 到媒资。

-- --------------------------------------------------
-- 1) 线上书元数据
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS ebook_book (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id           BIGINT UNSIGNED NULL,
  title             VARCHAR(128) NOT NULL,
  author            VARCHAR(64) NULL,
  cover_url         VARCHAR(512) NULL,
  format            VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN',
  status            TINYINT NOT NULL DEFAULT 1,
  preview_mode      VARCHAR(16) NOT NULL DEFAULT 'CHAPTER',
  preview_chapters  INT NOT NULL DEFAULT 2,
  total_chapters    INT NOT NULL DEFAULT 0,
  word_count        BIGINT NOT NULL DEFAULT 0,
  source_file_id    VARCHAR(64) NULL COMMENT '媒资 DOCUMENT',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ebook_book_id (book_id),
  KEY idx_ebook_source (source_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线上书元数据';

-- --------------------------------------------------
-- 2) 章节目录（无正文）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS ebook_chapter (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  ebook_id          BIGINT UNSIGNED NOT NULL,
  chapter_no        INT NOT NULL,
  title             VARCHAR(128) NOT NULL,
  chapter_file_id   VARCHAR(64) NOT NULL COMMENT '媒资 CHAPTER',
  word_count        INT NOT NULL DEFAULT 0,
  is_preview_free   TINYINT NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ebook_chapter (ebook_id, chapter_no),
  UNIQUE KEY uk_chapter_file (chapter_file_id),
  CONSTRAINT fk_chapter_ebook FOREIGN KEY (ebook_id) REFERENCES ebook_book(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节目录，无正文';

-- --------------------------------------------------
-- 3) 阅读进度（用户 × 书唯一）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS ebook_reading_progress (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  ebook_id     BIGINT UNSIGNED NOT NULL,
  chapter_id   BIGINT UNSIGNED NOT NULL,
  char_offset  INT NOT NULL DEFAULT 0,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_progress_user_ebook (user_id, ebook_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线上书阅读进度';

-- --------------------------------------------------
-- 4) 用户笔记
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS user_note (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  ebook_id     BIGINT UNSIGNED NULL,
  chapter_id   BIGINT UNSIGNED NULL,
  book_id      BIGINT UNSIGNED NULL,
  file_id      VARCHAR(64) NULL,
  source_type  VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
    COMMENT 'MANUAL/HIGHLIGHT/AI_SUMMARY/AI_REWRITE/AI_MERGE/VIDEO_SUMMARY',
  title        VARCHAR(128) NULL,
  content      MEDIUMTEXT NOT NULL,
  quote_text   VARCHAR(1024) NULL,
  tags         VARCHAR(255) NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_note_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户阅读笔记';

-- --------------------------------------------------
-- 5) Agent 总结缓存（B5）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS reader_ai_summary_cache (
  id         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  cache_key  VARCHAR(128) NOT NULL,
  content    MEDIUMTEXT NOT NULL,
  model      VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_summary_cache_key (cache_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习 Agent 总结缓存';

-- --------------------------------------------------
-- 6) 图书配套视频绑定（B6）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS book_media_ref (
  id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id          BIGINT UNSIGNED NOT NULL,
  file_id          VARCHAR(64) NOT NULL,
  title            VARCHAR(128) NULL,
  media_type       VARCHAR(16) NOT NULL DEFAULT 'INTRO',
  preview_seconds  INT NOT NULL DEFAULT 300,
  sort_order       INT NOT NULL DEFAULT 0,
  status           TINYINT NOT NULL DEFAULT 1,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_book_file (book_id, file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书配套视频绑定';
