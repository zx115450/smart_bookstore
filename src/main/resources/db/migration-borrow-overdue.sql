-- 借阅逾期扫描索引（已有库按需执行）
ALTER TABLE borrow_order
  ADD KEY idx_borrow_status_due (status, due_at);
