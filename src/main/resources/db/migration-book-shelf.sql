-- 书架与图书位置（已有库升级脚本，可重复执行）
-- 执行示例：mysql -uroot -p --default-character-set=utf8mb4 test_demo < migration-book-shelf.sql
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS bookshelf (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  floor       INT NOT NULL COMMENT '所在楼层（第几楼）',
  code        VARCHAR(32) NOT NULL COMMENT '书架编号，如 A-01',
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bookshelf_floor_code (floor, code),
  KEY idx_bookshelf_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='书架';

SET @col_bookshelf_id = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book' AND COLUMN_NAME = 'bookshelf_id'
);
SET @sql_bookshelf_id = IF(
  @col_bookshelf_id = 0,
  'ALTER TABLE book ADD COLUMN bookshelf_id BIGINT UNSIGNED NULL COMMENT ''所在书架（可借图书须配置）'' AFTER borrow_days',
  'SELECT 1'
);
PREPARE stmt FROM @sql_bookshelf_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_shelf_layer = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book' AND COLUMN_NAME = 'shelf_layer'
);
SET @sql_shelf_layer = IF(
  @col_shelf_layer = 0,
  'ALTER TABLE book ADD COLUMN shelf_layer INT NULL COMMENT ''书架层数（从上往下第几层）'' AFTER bookshelf_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql_shelf_layer;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_book_bookshelf = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book' AND CONSTRAINT_NAME = 'fk_book_bookshelf'
);
SET @sql_fk = IF(
  @fk_book_bookshelf = 0,
  'ALTER TABLE book ADD CONSTRAINT fk_book_bookshelf FOREIGN KEY (bookshelf_id) REFERENCES bookshelf(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_book_bookshelf = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'book' AND INDEX_NAME = 'idx_book_bookshelf'
);
SET @sql_idx = IF(
  @idx_book_bookshelf = 0,
  'ALTER TABLE book ADD KEY idx_book_bookshelf (bookshelf_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO bookshelf (floor, code, status)
SELECT 2, 'A-01', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bookshelf WHERE floor = 2 AND code = 'A-01');

INSERT INTO bookshelf (floor, code, status)
SELECT 2, 'A-02', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bookshelf WHERE floor = 2 AND code = 'A-02');

INSERT INTO bookshelf (floor, code, status)
SELECT 3, 'B-01', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bookshelf WHERE floor = 3 AND code = 'B-01');

UPDATE book b
JOIN bookshelf s ON s.floor = 2 AND s.code = 'A-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 3
WHERE b.title = 'Java 核心技术' AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 2 AND s.code = 'A-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 2
WHERE b.title = 'Spring Boot 实战' AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 2 AND s.code = 'A-02'
SET b.bookshelf_id = s.id, b.shelf_layer = 4
WHERE b.title = 'Redis 设计与实现' AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 3 AND s.code = 'B-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 2
WHERE b.title = '红楼梦' AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 3 AND s.code = 'B-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 1
WHERE b.title = '三国演义' AND b.bookshelf_id IS NULL;
