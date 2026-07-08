-- 已有库升级：签到 + 优惠券 + 余额支付（按需手动执行）
-- mysql -u root -p test_demo < src/main/resources/db/migration-checkin.sql

ALTER TABLE auth_user
  ADD COLUMN balance DECIMAL(10,2) NOT NULL DEFAULT 100.00 COMMENT '账户余额（模拟支付）' AFTER password_hash;

UPDATE auth_user SET balance = 100.00 WHERE balance = 0;

ALTER TABLE reservation_order
  ADD COLUMN checkin_at DATETIME NULL COMMENT '到馆签到时间' AFTER completed_at;

CREATE TABLE IF NOT EXISTS checkin_record (
  id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,
  checkin_date          DATE NOT NULL,
  reservation_order_id  BIGINT UNSIGNED NOT NULL,
  venue_id              BIGINT UNSIGNED NOT NULL,
  streak_day            INT NOT NULL DEFAULT 1,
  reward_coupon_id      BIGINT UNSIGNED NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_checkin_user_date (user_id, checkin_date),
  KEY idx_checkin_user (user_id),
  KEY idx_checkin_order (reservation_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS checkin_streak (
  user_id           BIGINT UNSIGNED PRIMARY KEY,
  current_streak    INT NOT NULL DEFAULT 0,
  last_checkin_date DATE NULL,
  total_checkins    INT NOT NULL DEFAULT 0,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_streak_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_template (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL,
  coupon_type     ENUM('FIXED','PERCENT') NOT NULL DEFAULT 'FIXED',
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_count     INT NOT NULL DEFAULT 0,
  issued_count    INT NOT NULL DEFAULT 0,
  valid_days      INT NOT NULL DEFAULT 7,
  status          TINYINT NOT NULL DEFAULT 1,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_coupon (
  id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  template_id   BIGINT UNSIGNED NOT NULL,
  status        ENUM('UNUSED','USED','EXPIRED') NOT NULL DEFAULT 'UNUSED',
  obtain_way    ENUM('SECKILL','CHECKIN_7','ADMIN') NOT NULL,
  expire_at     DATETIME NOT NULL,
  used_at       DATETIME NULL,
  trade_order_id BIGINT UNSIGNED NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_uc_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
  KEY idx_uc_user_status (user_id, status),
  KEY idx_uc_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trade_order (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_no        VARCHAR(32) NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  total_amount    DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  pay_amount      DECIMAL(10,2) NOT NULL,
  coupon_id       BIGINT UNSIGNED NULL,
  status          ENUM('PENDING_PAY','PAID','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING_PAY',
  idempotency_key VARCHAR(64) NULL,
  paid_at         DATETIME NULL,
  cancelled_at    DATETIME NULL,
  completed_at    DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_trade_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_trade_order_no (order_no),
  UNIQUE KEY uk_trade_idempotency (idempotency_key),
  KEY idx_trade_user (user_id),
  KEY idx_trade_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS trade_order_item (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  book_id     BIGINT UNSIGNED NOT NULL,
  book_title  VARCHAR(128) NOT NULL,
  price       DECIMAL(10,2) NOT NULL,
  quantity    INT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_trade_item_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
  CONSTRAINT fk_trade_item_book FOREIGN KEY (book_id) REFERENCES book(id),
  KEY idx_trade_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT 'CHECKIN_7', 'FIXED', 0.00, 5.00, 0, 14, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = 'CHECKIN_7');

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT '满50减10', 'FIXED', 50.00, 10.00, 1000, 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满50减10');
