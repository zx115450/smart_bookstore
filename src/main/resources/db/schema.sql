-- 初始化数据库脚本 (MySQL 8+)
-- 说明：如果你希望脚本直接创建库，请取消下面的 CREATE DATABASE 注释。通常在生产环境中，DB管理员会负责创建数据库和用户。

-- CREATE DATABASE IF NOT EXISTS factory_auth
--   DEFAULT CHARACTER SET utf8mb4
--   DEFAULT COLLATE utf8mb4_0900_ai_ci;
-- USE factory_auth;

-- --------------------------------------------------
-- 1) 用户主表
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username          VARCHAR(64) NOT NULL,
  password_hash     VARCHAR(255) NULL COMMENT '密码登录用；验证码登录用户可为空',
  balance           DECIMAL(10,2) NOT NULL DEFAULT 100.00 COMMENT '账户余额（模拟支付）',
  status            TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常, 0=禁用',
  last_login_at     DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户基础信息';

-- --------------------------------------------------
-- 2) 用户登录标识表（邮箱/手机/账号）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user_identity (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  identity_type     ENUM('email','phone','account','qq_openid') NOT NULL,
  identity_value    VARCHAR(128) NOT NULL,
  verified          TINYINT NOT NULL DEFAULT 0 COMMENT '1=已验证',
  is_primary        TINYINT NOT NULL DEFAULT 0 COMMENT '该类型是否主标识',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_identity_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_identity_type_value (identity_type, identity_value),
  KEY idx_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录标识（邮箱/手机/账号）';

-- --------------------------------------------------
-- 3) 验证码记录表（建议存哈希）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_verification_code (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  login_type        ENUM('qq_email_code','phone_code') NOT NULL,
  target            VARCHAR(128) NOT NULL COMMENT '邮箱或手机号',
  scene             VARCHAR(32) NOT NULL COMMENT '如 login',
  code_hash         VARCHAR(255) NOT NULL COMMENT '建议存哈希，不存明文',
  expires_at        DATETIME NOT NULL,
  used_at           DATETIME NULL,
  send_ip           VARCHAR(45) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_code_target_scene (target, scene),
  KEY idx_code_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='验证码记录';

-- --------------------------------------------------
-- 4) 会话/刷新令牌表（支持 rememberMe）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_session (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  refresh_token_jti CHAR(36) NOT NULL COMMENT 'refresh token唯一ID（建议UUID）',
  refresh_token_hash VARCHAR(255) NOT NULL COMMENT '建议存哈希',
  access_expires_at DATETIME NOT NULL,
  refresh_expires_at DATETIME NOT NULL,
  absolute_expires_at DATETIME NOT NULL COMMENT '会话绝对过期（首次登录起算，refresh 不延长）',
  remember_me       TINYINT NOT NULL DEFAULT 0,
  device_info       VARCHAR(255) NULL,
  login_ip          VARCHAR(45) NULL,
  revoked_at        DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_refresh_jti (refresh_token_jti),
  KEY idx_session_user (user_id),
  KEY idx_session_refresh_expires (refresh_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话与刷新令牌';

-- --------------------------------------------------
-- 5) 登录审计表（强烈建议启用）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_login_audit (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NULL,
  login_type        ENUM('qq_email_code','phone_code','password','qq_oauth') NOT NULL,
  target            VARCHAR(128) NULL COMMENT '邮箱/手机号/账号',
  success           TINYINT NOT NULL COMMENT '1=成功,0=失败',
  fail_reason       VARCHAR(128) NULL,
  ip                VARCHAR(45) NULL,
  user_agent        VARCHAR(255) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_user (user_id),
  KEY idx_audit_target (target),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录行为审计';

-- --------------------------------------------------
-- 6) RBAC 角色表
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_role (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  code              VARCHAR(32) NOT NULL COMMENT '角色编码，如 USER',
  name              VARCHAR(64) NOT NULL COMMENT '角色名称',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS auth_user_role (
  user_id           BIGINT UNSIGNED NOT NULL,
  role_id           BIGINT UNSIGNED NOT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES auth_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

-- --------------------------------------------------
-- 初始化角色与示例账号（密码 123456 的 BCrypt 哈希）
-- --------------------------------------------------
INSERT INTO auth_role (code, name)
SELECT 'USER', '普通用户'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE code = 'USER');

INSERT INTO auth_role (code, name)
SELECT 'ADMIN', '管理员'
WHERE NOT EXISTS (SELECT 1 FROM auth_role WHERE code = 'ADMIN');

INSERT INTO auth_user (username, password_hash, status)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1
WHERE NOT EXISTS (SELECT 1 FROM auth_user WHERE username = 'admin');

INSERT INTO auth_user_role (user_id, role_id)
SELECT u.id, r.id
FROM auth_user u, auth_role r
WHERE u.username = 'admin' AND r.code = 'USER'
  AND NOT EXISTS (
    SELECT 1 FROM auth_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO auth_user_role (user_id, role_id)
SELECT u.id, r.id
FROM auth_user u, auth_role r
WHERE u.username = 'admin' AND r.code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM auth_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- --------------------------------------------------
-- 7) 自习室预约：资源 / 座位 / 时段 / 预约单
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation_resource (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name              VARCHAR(64) NOT NULL COMMENT '自习室名称',
  location          VARCHAR(128) NOT NULL COMMENT '位置',
  total_capacity    INT NOT NULL COMMENT '总座位数',
  status            TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=下架',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_res_resource_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约资源-自习室';

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

CREATE TABLE IF NOT EXISTS reservation_time_slot (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  resource_id       BIGINT UNSIGNED NOT NULL,
  slot_date         DATE NOT NULL,
  start_time        TIME NOT NULL,
  end_time          TIME NOT NULL,
  capacity          INT NOT NULL COMMENT '该时段名额',
  booked_count      INT NOT NULL DEFAULT 0 COMMENT '已预约数',
  version           INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_slot_resource FOREIGN KEY (resource_id) REFERENCES reservation_resource(id),
  UNIQUE KEY uk_slot_resource_date_start (resource_id, slot_date, start_time),
  KEY idx_slot_date (slot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约时段';

CREATE TABLE IF NOT EXISTS reservation_order (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_no          VARCHAR(32) NOT NULL,
  user_id           BIGINT UNSIGNED NOT NULL,
  resource_id       BIGINT UNSIGNED NOT NULL,
  time_slot_id      BIGINT UNSIGNED NOT NULL,
  seat_id           BIGINT UNSIGNED NOT NULL COMMENT '预约的具体座位',
  status            ENUM('BOOKED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'BOOKED',
  cancel_reason     VARCHAR(255) NULL,
  idempotency_key   VARCHAR(64) NULL COMMENT '幂等键',
  booked_at         DATETIME NOT NULL,
  cancelled_at      DATETIME NULL,
  completed_at      DATETIME NULL,
  checkin_at        DATETIME NULL COMMENT '到馆签到时间',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_order_resource FOREIGN KEY (resource_id) REFERENCES reservation_resource(id),
  CONSTRAINT fk_order_slot FOREIGN KEY (time_slot_id) REFERENCES reservation_time_slot(id),
  CONSTRAINT fk_order_seat FOREIGN KEY (seat_id) REFERENCES reservation_seat(id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_order_idempotency (idempotency_key),
  KEY idx_order_user (user_id),
  KEY idx_order_slot (time_slot_id),
  KEY idx_order_seat (seat_id),
  KEY idx_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约单';

-- 示例自习室与时段（仅首次初始化）
INSERT INTO reservation_resource (name, location, total_capacity, status)
SELECT '图书馆 A 区', '主楼 3 层东侧', 30, 1
WHERE NOT EXISTS (SELECT 1 FROM reservation_resource WHERE name = '图书馆 A 区');

-- --------------------------------------------------
-- 8) 书城：图书分类与图书（P0）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS book_category (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  sort        INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书分类';

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

-- --------------------------------------------------
-- 8.1) 书城：库存流水（审计）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS book_stock_log (
  id          BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id     BIGINT UNSIGNED NOT NULL,
  change_type ENUM('SALE_OUT','SALE_IN','BORROW_OUT','BORROW_IN','ADMIN_ADJUST') NOT NULL,
  change_qty  INT NOT NULL COMMENT '变动数量（正数）',
  ref_type    VARCHAR(32) NULL COMMENT 'trade_order/borrow_order/admin',
  ref_id      BIGINT UNSIGNED NULL,
  operator_id BIGINT UNSIGNED NULL,
  remark      VARCHAR(255) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_stock_log_book FOREIGN KEY (book_id) REFERENCES book(id),
  KEY idx_stock_log_book (book_id),
  KEY idx_stock_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图书库存流水';

INSERT INTO book_category (name, sort, status)
SELECT '技术', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '技术');

INSERT INTO book_category (name, sort, status)
SELECT '文学', 2, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '文学');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787111213826', 'Java 核心技术', 'Cay S. Horstmann', 119.00, 50, 10, 1, 'Java 经典教程'
FROM book_category c
WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = 'Java 核心技术');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787115546081', 'Spring Boot 实战', ' Craig Walls', 79.00, 30, 8, 1, 'Spring Boot 入门与进阶'
FROM book_category c
WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = 'Spring Boot 实战');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787115428028', 'Redis 设计与实现', '黄健宏', 79.00, 20, 5, 1, 'Redis 内部机制'
FROM book_category c
WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = 'Redis 设计与实现');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787020002207', '红楼梦', '曹雪芹', 59.00, 100, 20, 1, '中国古典四大名著'
FROM book_category c
WHERE c.name = '文学'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '红楼梦');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787020008735', '三国演义', '罗贯中', 49.00, 80, 15, 1, '中国古典四大名著'
FROM book_category c
WHERE c.name = '文学'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '三国演义');

-- --------------------------------------------------
-- 9) 书城：借阅单（P1）
-- --------------------------------------------------
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
  KEY idx_borrow_book (book_id),
  KEY idx_borrow_status (status),
  KEY idx_borrow_status_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅单';

-- --------------------------------------------------
-- 10) 签到（预约到馆打卡）
-- --------------------------------------------------
CREATE TABLE IF NOT EXISTS checkin_record (
  id                    BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id               BIGINT UNSIGNED NOT NULL,
  checkin_date          DATE NOT NULL,
  reservation_order_id  BIGINT UNSIGNED NOT NULL COMMENT '关联预约单',
  venue_id              BIGINT UNSIGNED NOT NULL COMMENT '扫码场馆',
  streak_day            INT NOT NULL DEFAULT 1 COMMENT '当日是连续第几天',
  reward_coupon_id      BIGINT UNSIGNED NULL COMMENT '若当日触发7天奖励',
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  UNIQUE KEY uk_checkin_user_date (user_id, checkin_date),
  KEY idx_checkin_user (user_id),
  KEY idx_checkin_order (reservation_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到记录';

CREATE TABLE IF NOT EXISTS checkin_streak (
  user_id           BIGINT UNSIGNED PRIMARY KEY,
  current_streak    INT NOT NULL DEFAULT 0,
  last_checkin_date DATE NULL,
  total_checkins    INT NOT NULL DEFAULT 0,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_streak_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户连续签到状态';

-- --------------------------------------------------
-- 11) 优惠券
-- --------------------------------------------------
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

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT 'CHECKIN_7', 'FIXED', 0.00, 5.00, 0, 14, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = 'CHECKIN_7');

INSERT INTO coupon_template (name, coupon_type, threshold_amount, discount_amount, total_count, valid_days, status)
SELECT '满50减10', 'FIXED', 50.00, 10.00, 1000, 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM coupon_template WHERE name = '满50减10');

-- --------------------------------------------------
-- 11.5) 购物车
-- --------------------------------------------------
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

-- --------------------------------------------------
-- 11.6) 秒杀活动
-- --------------------------------------------------
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

-- --------------------------------------------------
-- 12) 购书订单（余额模拟支付）
-- --------------------------------------------------
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

-- 使用说明：将本文件放在资源目录并在 DB 管理工具中执行，或由 CI/CD 在初始化阶段运行。
--
-- 已有库升级（座位粒度，按需手动执行）：
-- CREATE TABLE reservation_seat (...);  -- 见上方完整定义
-- ALTER TABLE reservation_order ADD COLUMN seat_id BIGINT UNSIGNED NULL AFTER time_slot_id;
-- ALTER TABLE reservation_order ADD CONSTRAINT fk_order_seat FOREIGN KEY (seat_id) REFERENCES reservation_seat(id);
-- ALTER TABLE reservation_order MODIFY seat_id BIGINT UNSIGNED NOT NULL;
-- ALTER TABLE auth_user_identity MODIFY identity_type ENUM('email','phone','account','qq_openid') NOT NULL;
-- ALTER TABLE auth_login_audit MODIFY login_type ENUM('qq_email_code','phone_code','password','qq_oauth') NOT NULL;
-- ALTER TABLE auth_session ADD COLUMN absolute_expires_at DATETIME NULL COMMENT '会话绝对过期' AFTER refresh_expires_at;
-- UPDATE auth_session SET absolute_expires_at = DATE_ADD(created_at, INTERVAL 90 DAY) WHERE absolute_expires_at IS NULL;
-- ALTER TABLE auth_session MODIFY absolute_expires_at DATETIME NOT NULL;

