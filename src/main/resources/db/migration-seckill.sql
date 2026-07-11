-- 已有库升级：P4 秒杀活动 + 参与记录（按需手动执行）
-- mysql --user=root --password=1234 --host=127.0.0.1 --port=3306 test_demo < src/main/resources/db/migration-seckill.sql

-- 秒杀用券模板（满50减10，若不存在则插入）
INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT '满50减10', 'FIXED', 50.00, 10.00, 1000, 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满50减10');

CREATE TABLE IF NOT EXISTS seckill_activity (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL,
  template_id     BIGINT UNSIGNED NOT NULL COMMENT '发放的券模板',
  seckill_stock   INT NOT NULL COMMENT '秒杀总量',
  start_time      DATETIME NOT NULL,
  end_time        DATETIME NOT NULL,
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=下架',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_seckill_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
  KEY idx_seckill_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀活动';

CREATE TABLE IF NOT EXISTS seckill_order (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  activity_id     BIGINT UNSIGNED NOT NULL,
  status          ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING',
  idempotency_key VARCHAR(64) NULL,
  user_coupon_id  BIGINT UNSIGNED NULL,
  fail_reason     VARCHAR(255) NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_so_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_so_activity FOREIGN KEY (activity_id) REFERENCES seckill_activity(id),
  UNIQUE KEY uk_seckill_user_activity (user_id, activity_id),
  UNIQUE KEY uk_seckill_idempotency (idempotency_key),
  KEY idx_so_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀参与记录';
