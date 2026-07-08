-- 座位粒度升级（已有库手动执行）
USE test_demo;

CREATE TABLE IF NOT EXISTS reservation_seat (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  resource_id       BIGINT UNSIGNED NOT NULL,
  seat_no           VARCHAR(16) NOT NULL COMMENT '座位编号，如 A01',
  row_num           INT NULL COMMENT '行号（布局用）',
  col_num           INT NULL COMMENT '列号（布局用）',
  status            TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_seat_resource FOREIGN KEY (resource_id) REFERENCES reservation_resource(id),
  UNIQUE KEY uk_seat_resource_no (resource_id, seat_no),
  KEY idx_seat_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自习室座位';

-- 为资源 1 生成 A01–A30（5 列 × 6 行）
INSERT INTO reservation_seat (resource_id, seat_no, row_num, col_num, status)
SELECT 1, CONCAT('A', LPAD(n, 2, '0')), CEIL(n / 5), ((n - 1) % 5) + 1, 1
FROM (
  SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
  UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
  UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15
  UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
  UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25
  UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION SELECT 30
) nums
WHERE NOT EXISTS (SELECT 1 FROM reservation_seat WHERE resource_id = 1 LIMIT 1);

-- 旧预约单无 seat_id，开发环境清空后改表
TRUNCATE TABLE reservation_order;

ALTER TABLE reservation_order
  ADD COLUMN seat_id BIGINT UNSIGNED NULL COMMENT '预约的具体座位' AFTER time_slot_id;

ALTER TABLE reservation_order
  ADD CONSTRAINT fk_order_seat FOREIGN KEY (seat_id) REFERENCES reservation_seat(id);

ALTER TABLE reservation_order
  MODIFY seat_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE reservation_order ADD KEY idx_order_seat (seat_id);
