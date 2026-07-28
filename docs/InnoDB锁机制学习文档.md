# 从书城预约 / 借阅学 InnoDB 行锁

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 关联本仓库业务代码  
> **关联包 / 模块**：`com.zx.reservation / com.zx.bookstore`  
> **建议前置**：[四层架构学习文档](./四层架构学习文档.md)、[幂等与行级锁学习文档](./幂等与行级锁学习文档.md)

## 学习目标

1. 理解 InnoDB 行锁在事务中的加锁时机
2. 读懂 Mapper 层 FOR UPDATE 与 @Transactional 的配合
3. 对比显式行锁与条件 UPDATE 两种并发手段

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/zx/reservation/mapper/ReservationSeatMapper.java` | 对照阅读 |
| `src/main/java/com/zx/bookstore/catalog/mapper/BookMapper.java` | 对照阅读 |
| `src/main/java/com/zx/bookstore/borrow/service/BorrowService.java` | 对照阅读 |

读理论时请打开上表文件对照；改代码前先跑通 `docker compose up -d` 与 `local` profile。

---

## 导读
做预约、库存、秒杀时，最常听到的一句话是：**「在事务里加行锁防超卖。」**

但很多人只知道写 `FOR UPDATE`，不清楚：

- 锁**什么时候**加、**什么时候**释放？
- 锁的粒度是「行」还是「表」？
- 为什么有索引和没索引，锁的行为完全不同？
- _gap 锁、死锁、隔离级别_ 和 `FOR UPDATE` 有什么关系？

本文按 **「先懂原理 → 再看项目 → 最后避坑」** 的顺序展开，不绑定特定 ORM，MyBatis / JPA 均适用。

---

## 一、InnoDB 为什么需要锁

InnoDB 支持事务（ACID）。多个事务同时读写同一行数据时，如果没有锁，会出现：

| 问题 | 现象 |
|------|------|
| **脏读** | 读到别的事务未提交的数据 |
| **不可重复读** | 同一事务内两次读结果不同 |
| **幻读** | 同一条件两次查，行数变多 |
| **超卖** | 两个事务都以为「还有库存」，各插入一条订单 |

**锁的作用**：在并发下协调事务对资源的访问顺序，保证一致性。

InnoDB 的锁是 **存储引擎层** 的，和 SQL 里的 `LOCK TABLES`（表锁）不是一回事。日常业务 99% 接触的是 InnoDB **行级锁**。

---

## 二、锁的生命周期：什么时候加、什么时候释放

### 2.1 核心规则（必记）

```text
InnoDB 的行锁在「事务提交或回滚」时释放，不是 SQL 语句执行完就释放。
```

```java
@Transactional
public OrderResponse createOrder(...) {
    // ① 事务开始（Spring 在方法进入时开启）

    seatRepository.findByIdForUpdate(seatId);  // ② 执行到这里才加锁
    orderRepository.existsBookedBySlotAndSeat(...);
    orderRepository.save(order);

    return buildOrderResponse(order);
    // ③ 方法正常结束 → commit → 释放锁
    //    或中间抛异常 → rollback → 也释放锁
}
```

| 时刻 | 发生了什么 |
|------|------------|
| 进入 `@Transactional` 方法 | 事务开始 |
| 执行 `SELECT ... FOR UPDATE` | **加行锁** |
| 后续 SELECT / INSERT | 锁一直持有 |
| `commit` / `rollback` | **释放所有本事务持有的行锁** |

### 2.2 为什么不能「查完就释放锁」？

如果 `FOR UPDATE` 语句结束就释放锁：

```text
事务 A：FOR UPDATE → 查占用「无」→ 释放锁
事务 B：FOR UPDATE → 查占用「无」→ 释放锁
事务 A：INSERT 订单
事务 B：INSERT 订单          ← 超卖
```

**检查与写入之间不能有空档**，所以锁必须持有到 INSERT 完成并 commit。  
这也是 **`@Transactional` 和 `FOR UPDATE` 必须配合** 的原因：没有事务，默认自动提交，每条 SQL 一个事务，锁会过早释放。

### 2.3 时间线图（两人抢同一座位）

```text
用户 A 事务                          用户 B 事务
─────────────────────────────────────────────────
FOR UPDATE seat_id=3  ✅ 加锁
exists 检查 → 无
INSERT order
commit  🔓 释放
                                     FOR UPDATE seat_id=3  ⏳ 阻塞等待
                                     ✅ 获得锁
                                     exists 检查 → 已有 BOOKED
                                     抛 3011 / rollback  🔓 释放
```

---

## 三、InnoDB 锁的主要类型

### 3.1 按粒度分

| 类型 | 说明 | 常见场景 |
|------|------|----------|
| **行锁（Record Lock）** | 锁住索引上的一条记录 | `FOR UPDATE` 命中唯一行 |
| **间隙锁（Gap Lock）** | 锁住索引记录之间的「间隙」 | 范围查询、防幻读 |
| **临键锁（Next-Key Lock）** | 行锁 + 间隙锁 | RR 隔离级别下默认 |
| **插入意向锁（Insert Intention Lock）** | INSERT 时在间隙上加的轻量锁 | 并发 INSERT |
| **意向锁（IX / IS）** | 表级，表示事务即将锁哪些行 | InnoDB 内部协调 |

日常口语说的「行级锁」，在 **Repeatable Read（RR）** 下往往是 **Next-Key Lock**，不只是「锁一行」那么简单。

### 3.2 按模式分

| 语句 | 锁模式 | 含义 |
|------|--------|------|
| `SELECT ... FOR UPDATE` | 排他锁（X） | 其他事务不能对该行再 `FOR UPDATE` / `UPDATE` / `DELETE` |
| `SELECT ... LOCK IN SHARE MODE` | 共享锁（S） | 可读，他人不能 X 锁修改 |
| `UPDATE` / `DELETE` | 排他锁（X） | 修改前对目标行加 X 锁 |
| 普通 `SELECT`（快照读） | **不加锁** | 读 MVCC 快照，一致性非锁定读 |

**预约占座用 `FOR UPDATE`（X 锁）**，因为后续要 INSERT 订单，需要排他占用。

---

## 四、两种读：快照读 vs 当前读

| | 快照读（Snapshot Read） | 当前读（Current Read） |
|--|-------------------------|-------------------------|
| **典型 SQL** | 普通 `SELECT` | `SELECT ... FOR UPDATE`、`UPDATE`、`DELETE` |
| **是否加锁** | 否 | 是 |
| **读什么** | 事务开始时或语句时的 MVCC 快照 | 最新已提交版本（并加锁） |
| **项目中的例子** | `existsBookedBySlotAndSeat` 里的 `selectCount` | `findByIdForUpdate` |

**防超卖组合**：

```text
当前读 FOR UPDATE 锁座位行  +  快照读查订单是否已存在  +  INSERT
         ↑                              ↑
    阻塞并发抢同一座              在锁保护下读最新数据（需注意隔离级别）
```

> **注意**：在 RR 下，普通 SELECT 是快照读，可能读到「旧快照」。  
> 你们项目在 `FOR UPDATE` 之后立刻 `existsBookedBySlotAndSeat`，且同一事务内已有 X 锁阻塞并发 INSERT，实践中可行。  
> 更保守的写法是对 `reservation_order` 也使用 `FOR UPDATE` 或依赖唯一索引兜底。

---

## 五、隔离级别与锁的关系

InnoDB 默认隔离级别：**Repeatable Read（可重复读）**。

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 锁的特点 |
|----------|------|------------|------|----------|
| READ UNCOMMITTED | 可能 | 可能 | 可能 | 几乎不用 |
| READ COMMITTED | 否 | 可能 | 可能 | 间隙锁较少，Oracle 类似 |
| **REPEATABLE READ** | 否 | 否 | 基本防住 | Next-Key Lock，MySQL 默认 |
| SERIALIZABLE | 否 | 否 | 否 | 读也加锁，性能差 |

**RR 下 `FOR UPDATE` 的范围查询**可能锁多行 + 间隙，容易放大锁冲突。  
**按主键精确查**（如 `WHERE id = ?`）通常只锁一行，是预约场景推荐写法：

```sql
SELECT * FROM reservation_seat WHERE id = ? FOR UPDATE
```

---

## 六、索引：决定锁给谁（非常重要）

InnoDB **锁的是索引记录**，不是「逻辑上的行」。

### 6.1 走主键 / 唯一索引

```sql
SELECT * FROM reservation_seat WHERE id = 3 FOR UPDATE;
```

→ 通常只锁 `id = 3` 这一条索引记录。✅ 推荐。

### 6.2 不走索引（全表扫描）

```sql
SELECT * FROM reservation_seat WHERE seat_no = 'A03' FOR UPDATE;
```

若 `seat_no` **没有索引**，InnoDB 可能扫描全表并对经过的行加锁，严重时接近 **锁全表**，并发崩溃。

**教训**：`FOR UPDATE` 的 WHERE 条件必须能命中 **主键或合适索引**。

### 6.3 范围条件

```sql
SELECT * FROM reservation_order
WHERE time_slot_id = 12 AND status = 'BOOKED'
FOR UPDATE;
```

锁的范围取决于索引：可能锁多行 + 间隙。高并发下尽量 **精确到主键**，避免大范围 `FOR UPDATE`。

---

## 七、结合项目：座位预约防超卖

### 7.1 当前实现

```java
@Transactional
public OrderResponse createOrder(...) {
    // ...
    ReservationSeat seat = seatRepository.findByIdForUpdate(req.getSeatId());
    // ...
    if (orderRepository.existsBookedBySlotAndSeat(slot.getId(), seat.getId())) {
        throw ReservationException.seatAlreadyBooked();  // 3011
    }
    orderRepository.save(order);
}
```

**三道防线**：

1. **事务** — 检查 + 插入原子性  
2. **`FOR UPDATE` 锁座位行** — 同一 `seat_id` 并发排队  
3. **`(time_slot_id, seat_id, BOOKED)` 存在性检查** — 业务层防重复占用  

### 7.2 还可加强的兜底

| 手段 | 说明 |
|------|------|
| **唯一索引** | 如对 `(time_slot_id, seat_id)` 建唯一约束（仅 BOOKED 需 partial index，MySQL 8.0.13+ 支持函数/条件索引较麻烦，可用应用层 + 锁为主） |
| **乐观锁** | `UPDATE slot SET booked_count = ... WHERE version = ?`，适合冲突少的场景 |
| **Redis 预减** | 高并发先减 Redis，再异步/同步写 DB |

当前 MVP：**悲观锁（FOR UPDATE）+ 事务** 足够清晰，面试也好讲。

---

## 八、必须注意的坑

### 8.1 事务没生效，锁白加

```java
// ❌ 同类内部自调用，@Transactional 可能不生效
public void book() {
    this.createOrder(...);
}

// ❌ 非 public 方法、异常被吞掉未 rollback
```

确认：`createOrder` 由 Spring 代理调用、`@Transactional` 在 public 方法上、异常能触发 rollback。

### 8.2 锁顺序不一致 → 死锁

```text
事务 A：先锁 seat#1，再锁 seat#2
事务 B：先锁 seat#2，再锁 seat#1
→ 可能互相等待，InnoDB 检测死锁，回滚其中一个
```

**建议**：多个资源加锁时，**按固定顺序**（如按 id 升序）加锁。

### 8.3 长事务占锁

`FOR UPDATE` 到 commit 之间若调用了慢 HTTP、睡线程、复杂计算，锁持有时间过长，其他用户抢同一座会长时间阻塞。

**建议**：锁内只做必要的 DB 操作，外部 IO 移到事务外。

### 8.4 快照读与当前读混用误解

在 `FOR UPDATE` **之前**用普通 SELECT 看到「可约」，不代表并发安全；安全边界以 **加锁后的检查 + INSERT** 为准。

### 8.5 `SELECT FOR UPDATE` 查不到行

```sql
SELECT * FROM reservation_seat WHERE id = 999 FOR UPDATE;
-- 行不存在 → 不加任何行锁
```

不存在的数据无法加行锁，**插入新行**的并发靠间隙锁 / 唯一索引约束。  
你们场景是「占已有座位」，行一定存在，这条影响较小。

### 8.6 与 `@Transactional(readOnly = true)` 冲突

只读事务里不能指望 `FOR UPDATE` 做占库存，占座写操作必须在读写事务中。

### 8.7 连接池与事务边界

一个 HTTP 请求通常对应一个连接、一个事务。不要在事务中跨线程换连接，否则锁在 A 连接上，B 连接看不到事务上下文。

---

## 九、死锁：怎么认、怎么处理

### 9.1 典型死锁日志

```text
ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
```

查看最近死锁：

```sql
SHOW ENGINE INNODB STATUS\G
-- 关注 LATEST DETECTED DEADLOCK 段
```

### 9.2 处理策略

| 策略 | 说明 |
|------|------|
| **统一加锁顺序** | 按 id 排序后依次 `FOR UPDATE` |
| **缩小事务** | 减少锁持有时间 |
| **重试** | 捕获 1213，有限次数重试（秒杀常见） |
| **降低隔离级别** | 一般不建议随意改，需评估 |

InnoDB 会自动 **回滚代价较小的事务**，应用层应捕获并重试或提示用户。

---

## 十、FOR UPDATE vs 乐观锁 vs Redis

| 方案 | 思路 | 优点 | 缺点 |
|------|------|------|------|
| **悲观锁 `FOR UPDATE`** | 先锁再查再写 | 实现直观，强一致 | 阻塞，热点行并发差 |
| **乐观锁 `version`** | 更新时 `WHERE version=?` | 无阻塞，读多写少友好 | 冲突多时要重试 |
| **Redis 预减库存** | 内存原子 DECR | 极高 QPS | 需与 DB 对账，架构复杂 |
| **唯一索引** | DB 层拒绝重复 | 最终兜底 | 需设计好键，错误要转业务码 |

**自习室预约（座位级、并发中等）**：`FOR UPDATE` 合适。  
**大促秒杀（万级 QPS）**：往往 Redis + 异步落库。

---

## 十一、开发自检清单

- [ ] `FOR UPDATE` 的 WHERE 能走 **主键/唯一索引**
- [ ] 占库存逻辑在 **`@Transactional` 方法**内，且锁持有到 INSERT commit
- [ ] 事务内 **无慢调用、无远程 IO**
- [ ] 多资源加锁有 **固定顺序**，避免死锁
- [ ] 理解普通 SELECT 是 **快照读**，不能单独用来防超卖
- [ ] 生产环境开启 **慢查询 / 死锁监控**
- [ ] 可选：**唯一约束** 作最后一道防线

---

## 十二、常用 SQL 与命令

```sql
-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 查看 InnoDB 锁等待（MySQL 8.0）
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;

-- 查看最近死锁
SHOW ENGINE INNODB STATUS\G

-- 当前会话
SELECT @@autocommit;  -- 0 表示需手动 commit（Spring 事务管理通常自动）
```

---

## 十三、一句话总结

```text
InnoDB 行锁在事务 commit/rollback 时释放，不是语句结束就释放。
FOR UPDATE 在「执行到该 SQL」时加锁，必须配合 @Transactional 覆盖「检查 + 写入」全程。
锁的是索引记录，WHERE 必须走索引，否则可能锁全表。
RR 下默认 Next-Key Lock，精确主键查询锁范围最小。
预约防超卖 = 事务 + FOR UPDATE 锁座位 + 占用检查 + INSERT；注意死锁、长事务、事务失效。
```

---

## 相关文档

- [幂等与行级锁学习文档](./幂等与行级锁学习文档.md) — createOrder 完整代码 + 锁生命周期
- [MySQL锁与业务场景学习文档](./MySQL锁与业务场景学习文档.md) — 不同业务场景下锁的选型（通用）
- [预约系统业务价值点](./预约系统业务价值点.md)
- [自习室预约业务流程](./自习室预约业务流程.md)
- [四层架构学习文档](./四层架构学习文档.md)
