-- P1 借阅单表（已有库按需执行）
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
  KEY idx_borrow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='借阅单';
