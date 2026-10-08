ALTER TABLE borrow_order
  ADD COLUMN renew_count INT NOT NULL DEFAULT 0 COMMENT '已续借次数' AFTER return_at;
