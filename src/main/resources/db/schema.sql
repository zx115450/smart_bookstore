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
  identity_type     ENUM('email','phone','account') NOT NULL,
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
  login_type        ENUM('qq_email_code','phone_code','password') NOT NULL,
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
-- 可选：插入一个示例管理员账号（请替换 password_hash 为真实哈希）
-- password_hash 示例为 bcrypt/argon2 等算法的输出，不要存明文密码
-- --------------------------------------------------
INSERT INTO auth_user (username, password_hash, status)
SELECT 'admin', '<REPLACE_WITH_BCRYPT_HASH>', 1
WHERE NOT EXISTS (SELECT 1 FROM auth_user WHERE username = 'admin');

-- 若需绑定 email/phone，请在 auth_user_identity 中插入
-- INSERT INTO auth_user_identity (user_id, identity_type, identity_value, verified, is_primary)
-- VALUES (1, 'email', 'admin@example.com', 1, 1);

-- 使用说明：将本文件放在资源目录并在 DB 管理工具中执行，或由 CI/CD 在初始化阶段运行。

