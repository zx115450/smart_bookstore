-- ============================================================
-- 智慧书城管理系统 — 数据库表草案
-- 说明：在现有 auth_* / reservation_* 表基础上追加
-- 执行：按需合并到 schema.sql 或由 Flyway/Liquibase 管理
-- 最后更新：2026-07
-- ============================================================

USE test_demo;

-- ----------------------------------------------------------
-- 1) 角色扩展（可选）
-- ----------------------------------------------------------
INSERT INTO auth_role (code, name)
SELECT 'STAFF', '店员'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE code = 'STAFF');

-- ----------------------------------------------------------
-- 2) 预约单扩展：到馆签到时间
-- ----------------------------------------------------------
-- ALTER TABLE reservation_order
--   ADD COLUMN checkin_at DATETIME NULL COMMENT '到馆签到时间' AFTER completed_at;

-- ----------------------------------------------------------
-- 3) 图书分类
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS book_category (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  sort        INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类';

-- ----------------------------------------------------------
-- 4) 图书
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS book (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  category_id     BIGINT UNSIGNED NOT NULL,
  isbn            VARCHAR(20) NULL,
  title           VARCHAR(128) NOT NULL,
  author          VARCHAR(64) NULL,
  cover_url       VARCHAR(512) NULL,
  price           DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '售价',
  sale_stock      INT NOT NULL DEFAULT 0 COMMENT '可售库存',
  borrow_stock    INT NOT NULL DEFAULT 0 COMMENT '可借册数',
  borrow_days     INT NOT NULL DEFAULT 30 COMMENT '默认借阅天数',
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
  description     TEXT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES book_category(id),
  KEY idx_book_category (category_id),
  KEY idx_book_status (status),
  KEY idx_book_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书';

-- ----------------------------------------------------------
-- 5) 库存流水（可选，面试加分）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS book_stock_log (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id     BIGINT UNSIGNED NOT NULL,
  change_type ENUM('SALE_OUT','SALE_IN','BORROW_OUT','BORROW_IN','ADMIN_ADJUST') NOT NULL,
  change_qty  INT NOT NULL,
  ref_type    VARCHAR(32) NULL COMMENT 'trade_order/borrow_order/admin',
  ref_id      BIGINT UNSIGNED NULL,
  operator_id BIGINT UNSIGNED NULL,
  remark      VARCHAR(255) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_stock_log_book FOREIGN KEY (book_id) REFERENCES book(id),
  KEY idx_stock_log_book (book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书库存流水';

-- ----------------------------------------------------------
-- 6) 借阅单
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS borrow_order (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_no    VARCHAR(32) NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  book_id     BIGINT UNSIGNED NOT NULL,
  status      ENUM('APPLIED','BORROWED','RETURNED','OVERDUE','CANCELLED') NOT NULL DEFAULT 'APPLIED',
  borrow_at   DATETIME NULL,
  due_at      DATETIME NULL,
  return_at   DATETIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_borrow_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_borrow_book FOREIGN KEY (book_id) REFERENCES book(id),
  UNIQUE KEY uk_borrow_order_no (order_no),
  KEY idx_borrow_user (user_id),
  KEY idx_borrow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅单';

-- ----------------------------------------------------------
-- 7) 购物车
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS cart_item (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  book_id     BIGINT UNSIGNED NOT NULL,
  quantity    INT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_cart_book FOREIGN KEY (book_id) REFERENCES book(id),
  UNIQUE KEY uk_cart_user_book (user_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车';

-- ----------------------------------------------------------
-- 8) 购书订单
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_order (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_no        VARCHAR(32) NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  total_amount    DECIMAL(10,2) NOT NULL COMMENT '商品总额',
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  pay_amount      DECIMAL(10,2) NOT NULL COMMENT '实付',
  coupon_id       BIGINT UNSIGNED NULL COMMENT '使用的 user_coupon.id',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购书订单';

CREATE TABLE IF NOT EXISTS trade_order_item (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_id    BIGINT UNSIGNED NOT NULL,
  book_id     BIGINT UNSIGNED NOT NULL,
  book_title  VARCHAR(128) NOT NULL COMMENT '快照',
  price       DECIMAL(10,2) NOT NULL COMMENT '快照单价',
  quantity    INT NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_trade_item_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
  CONSTRAINT fk_trade_item_book FOREIGN KEY (book_id) REFERENCES book(id),
  KEY idx_trade_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购书订单明细';

-- ----------------------------------------------------------
-- 9) 优惠券
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS coupon_template (
  id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64) NOT NULL,
  coupon_type     ENUM('FIXED','PERCENT') NOT NULL DEFAULT 'FIXED' COMMENT '满减/折扣',
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛',
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '面额或折扣值',
  total_count     INT NOT NULL DEFAULT 0 COMMENT '发行总量，0=不限',
  issued_count    INT NOT NULL DEFAULT 0,
  valid_days      INT NOT NULL DEFAULT 7 COMMENT '领取后有效天数',
  status          TINYINT NOT NULL DEFAULT 1,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS user_coupon (
  id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  template_id   BIGINT UNSIGNED NOT NULL,
  status        ENUM('UNUSED','USED','EXPIRED') NOT NULL DEFAULT 'UNUSED',
  obtain_way    ENUM('SECKILL','CHECKIN_7','ADMIN') NOT NULL,
  expire_at     DATETIME NOT NULL,
  used_at       DATETIME NULL,
  trade_order_id BIGINT UNSIGNED NULL COMMENT '核销订单',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_uc_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_uc_template FOREIGN KEY (template_id) REFERENCES coupon_template(id),
  KEY idx_uc_user_status (user_id, status),
  KEY idx_uc_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户优惠券';

-- ----------------------------------------------------------
-- 10) 秒杀活动
-- ----------------------------------------------------------
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

-- ----------------------------------------------------------
-- 11) 签到（MySQL 持久化，Redis BitMap 为主时可作备份/对账）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkin_record (
  id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,
  checkin_date          DATE NOT NULL,
  reservation_order_id  BIGINT UNSIGNED NULL COMMENT '关联预约单',
  streak_day            INT NOT NULL DEFAULT 1 COMMENT '当日是连续第几天',
  reward_coupon_id      BIGINT UNSIGNED NULL COMMENT '若当日触发7天奖励',
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_checkin_user_date (user_id, checkin_date),
  KEY idx_checkin_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到记录';

CREATE TABLE IF NOT EXISTS checkin_streak (
  user_id           BIGINT UNSIGNED PRIMARY KEY,
  current_streak    INT NOT NULL DEFAULT 0,
  last_checkin_date DATE NULL,
  total_checkins    INT NOT NULL DEFAULT 0,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_streak_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户连续签到状态';

-- ----------------------------------------------------------
-- 12) 示例种子数据（可选）
-- ----------------------------------------------------------
INSERT INTO book_category (name, sort, status)
SELECT '技术', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '技术');

INSERT INTO book_category (name, sort, status)
SELECT '文学', 2, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '文学');

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT '满50减10', 'FIXED', 50.00, 10.00, 1000, 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满50减10');

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT '连续签到7天赠券', 'FIXED', 0.00, 5.00, 0, 14, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '连续签到7天赠券');
