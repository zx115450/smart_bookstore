-- 购书超时关单 DLQ 失败落库（已有库增量执行）
CREATE TABLE IF NOT EXISTS trade_order_timeout_fail (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NULL COMMENT '订单 id，消息残缺时可为 NULL',
  order_no     VARCHAR(32) NULL,
  fail_reason  VARCHAR(512) NOT NULL COMMENT 'DLQ 关单失败原因',
  status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=待补偿 RESOLVED=已处理',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_timeout_fail_status (status),
  KEY idx_timeout_fail_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购书超时关单 DLQ 失败落库';
