-- 库存流水表（已有库按需执行）
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
