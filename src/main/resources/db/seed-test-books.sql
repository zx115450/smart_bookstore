-- 书城测试数据（可重复执行；按 title / name 幂等）
-- 用法：mysql -uroot -p1234 test_demo < seed-test-books.sql
-- 注意：直接 SQL 插入不会走 createBook 的 Bloom add；需重启应用触发预热，或管理端新建。

SET NAMES utf8mb4;

INSERT INTO book_category (name, sort, status)
SELECT '历史', 3, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '历史');

INSERT INTO book_category (name, sort, status)
SELECT '科普', 4, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM book_category WHERE name = '科普');

-- 技术
INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787115428029', '深入理解 Java 虚拟机', '周志明', 129.00, 40, 8, 1, 'JVM 进阶'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '深入理解 Java 虚拟机');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787115352118', '高性能 MySQL', 'Baron Schwartz', 128.00, 25, 6, 1, 'MySQL 性能优化'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '高性能 MySQL');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787111544937', 'RabbitMQ 实战', 'Alvaro Videla', 69.00, 35, 7, 1, '消息队列入门'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = 'RabbitMQ 实战');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787121310942', '算法导论', 'Thomas H. Cormen', 128.00, 20, 5, 1, '算法经典教材'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '算法导论');

-- 文学
INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787020008728', '西游记', '吴承恩', 45.00, 90, 18, 1, '中国古典四大名著'
FROM book_category c WHERE c.name = '文学'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '西游记');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787020008742', '水浒传', '施耐庵', 48.00, 85, 16, 1, '中国古典四大名著'
FROM book_category c WHERE c.name = '文学'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '水浒传');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787020127481', '活着', '余华', 39.00, 60, 12, 1, '当代文学代表作'
FROM book_category c WHERE c.name = '文学'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '活着');

-- 历史
INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787101003048', '史记', '司马迁', 88.00, 30, 10, 1, '二十四史之首'
FROM book_category c WHERE c.name = '历史'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '史记');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787101003055', '资治通鉴', '司马光', 98.00, 22, 8, 1, '编年体通史'
FROM book_category c WHERE c.name = '历史'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '资治通鉴');

-- 科普
INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787535732309', '时间简史', '史蒂芬·霍金', 45.00, 50, 10, 1, '宇宙科普经典'
FROM book_category c WHERE c.name = '科普'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '时间简史');

INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787115417305', '代码整洁之道', 'Robert C. Martin', 59.00, 45, 9, 1, '软件工匠实践'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '代码整洁之道');

-- 一本下架书（测管理端 / Bloom 假阳性路径）
INSERT INTO book (category_id, isbn, title, author, price, sale_stock, borrow_stock, status, description)
SELECT c.id, '9787000000001', '已下架测试书', '测试作者', 9.90, 0, 0, 0, 'status=0，用户端应 404'
FROM book_category c WHERE c.name = '技术'
  AND NOT EXISTS (SELECT 1 FROM book WHERE title = '已下架测试书');

-- 分配书架（可借）
UPDATE book b
JOIN bookshelf s ON s.floor = 2 AND s.code = 'A-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 1
WHERE b.title IN ('深入理解 Java 虚拟机', '高性能 MySQL', '代码整洁之道')
  AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 2 AND s.code = 'A-02'
SET b.bookshelf_id = s.id, b.shelf_layer = 2
WHERE b.title IN ('RabbitMQ 实战', '算法导论', '时间简史')
  AND b.bookshelf_id IS NULL;

UPDATE book b
JOIN bookshelf s ON s.floor = 3 AND s.code = 'B-01'
SET b.bookshelf_id = s.id, b.shelf_layer = 1
WHERE b.title IN ('西游记', '水浒传', '活着', '史记', '资治通鉴')
  AND b.bookshelf_id IS NULL;

SELECT 'categories' AS kind, COUNT(*) AS cnt FROM book_category
UNION ALL
SELECT 'books', COUNT(*) FROM book
UNION ALL
SELECT 'books_on_shelf', COUNT(*) FROM book WHERE status = 1
UNION ALL
SELECT 'books_off_shelf', COUNT(*) FROM book WHERE status = 0;
