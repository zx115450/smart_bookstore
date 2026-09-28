# Spring Boot 使用 Binlog 学习文档


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：[Elasticsearch 学习文档](./Elasticsearch学习文档.md) · [数据库与缓存高一致性业务场景实现方案](../数据库与缓存高一致性业务场景实现方案.md) · [消息队列](./消息队列.md) · [缓存](./缓存.md)  
> **仓库现状**：业务写 MySQL / Redis / MQ，**未接入** Canal / Debezium；本篇讲「Spring Boot 如何消费 Binlog」的原理与落地路线，供演进缓存失效、搜推同步与面试加深

说明：Spring Boot **并不内置**「读 Binlog」的 starter。实务中是 **MySQL 写 Binlog → CDC 中间件解析 → Spring Boot 应用消费变更事件**。本文把这条链路讲透。

---

## 目录

1. [学习目标](#1-学习目标)
2. [与本项目的映射](#2-与本项目的映射)
3. [Binlog 是什么](#3-binlog-是什么)
4. [为什么要在 Spring Boot 里用 Binlog](#4-为什么要在-spring-boot-里用-binlog)
5. [总体架构](#5-总体架构)
6. [MySQL 侧必备配置](#6-mysql-侧必备配置)
7. [主流接入方式对比](#7-主流接入方式对比)
8. [Canal + Spring Boot 落地](#8-canal--spring-boot-落地)
9. [Debezium + Spring Boot 落地](#9-debezium--spring-boot-落地)
10. [消费端核心设计](#10-消费端核心设计)
11. [书城场景设计草案](#11-书城场景设计草案)
12. [双写 vs Binlog 怎么选](#12-双写-vs-binlog-怎么选)
13. [常见坑与运维](#13-常见坑与运维)
14. [分阶段学习路线](#14-分阶段学习路线)
15. [能力自检与面试口述](#15-能力自检与面试口述)

---

## 1. 学习目标

学完后应能：

1. 说明 Binlog 记什么、和 redo / undo 的分工
2. 画出「MySQL → CDC →（MQ）→ Spring Boot」至少一种架构
3. 配置 MySQL 开启 ROW Binlog，并说清为何 CDC 需要 `ROW`
4. 在消费端处理好：幂等、乱序、重试、表过滤、位点（offset）
5. 结合本仓库说出：哪些适合 Binlog 驱动（删缓存、同步 ES），哪些必须仍走事务内写 MySQL

预计投入：1.5～2.5 周（含本地 Docker 起 MySQL + Canal 或 Debezium，写一个最小消费者）。

---

## 2. 与本项目的映射

| 概念 | 本仓库现状 / 可演进 |
| --- | --- |
| 书目写库 | `BookCatalogService` / `BookRepository` → MySQL |
| 详情缓存 | Redis Cache-Aside、逻辑过期等（见缓存系列） |
| 缓存失效文档 | [高一致性方案](../数据库与缓存高一致性业务场景实现方案.md) 方案 E：Canal + MQ 删缓存 |
| 搜索同步 | [ES 学习文档](./Elasticsearch学习文档.md)：Binlog 为可选同步路径 |
| MQ | 秒杀 / 订单超时已用 RabbitMQ，可复用为 CDC 下游 |

当前 **不必** 为了学而强行接入；先把原理与最小 Demo 跑通，面试能讲清即可。

---

## 3. Binlog 是什么

### 3.1 定义

**Binlog（Binary Log）**：MySQL Server 层的二进制日志，按事务提交顺序记录 **数据变更**（以及部分 schema 变更），供：

- 主从复制
- 按时间点恢复（备份 + 重放 Binlog）
- CDC（Change Data Capture）：把变更推给外部系统

### 3.2 和 redo / undo 的区别

| 日志 | 层级 | 主要用途 |
| --- | --- | --- |
| redo log | InnoDB | 崩溃恢复、耐久性 |
| undo log | InnoDB | 回滚、MVCC |
| **binlog** | Server | 复制、恢复、**对外同步** |

口诀：**redo/undo 保引擎自己；binlog 让「别人」也能重放你的变更。**

### 3.3 三种格式

| `binlog_format` | 内容 | CDC 友好度 |
| --- | --- | --- |
| `STATEMENT` | 记 SQL 文本 | 差：难稳解析、有不确定性 |
| **`ROW`** | 记每行前后镜像 | **推荐**：事件即行级变更 |
| `MIXED` | 混合 | 一般仍建议 CDC 场景固定 `ROW` |

Spring Boot 消费侧拿到的，通常是已经被 Canal / Debezium **解析成 INSERT / UPDATE / DELETE + 行列数据** 的消息，而不是裸二进制。

### 3.4 位点（Position / GTID）

订阅者要记住「读到哪了」，否则重启会丢或重放：

| 方式 | 含义 |
| --- | --- |
| `file + pos` | 如 `mysql-bin.000003` + 偏移量 |
| **GTID** | 全局事务 ID，主从切换更稳，生产更推荐 |

CDC 组件负责持久化位点；你的 Spring Boot 若只消费 MQ，则位点多在 Canal Server / Debezium Connect / Kafka offset 上。

---

## 4. 为什么要在 Spring Boot 里用 Binlog

### 4.1 典型业务动机

| 场景 | 做法 |
| --- | --- |
| 缓存失效 | 表变更 → 删 Redis Key（多服务写同表时尤其合适） |
| 搜索同步 | `book` 变更 → Upsert / Delete ES 文档 |
| 读模型 / 宽表 | 订单变更 → 更新查询专用表或文档 |
| 数据总线 | 变更进 MQ，多个下游各自消费 |
| 审计 / 数仓 | 近实时入湖、入仓 |

### 4.2 相对「应用双写」的好处

```text
双写：
  Service 写 MySQL
  Service 再写 ES / 删 Redis
  → 业务代码耦合；漏写一个出口就漂；多服务难统一

Binlog：
  Service 只写 MySQL（事务真相）
  CDC 统一捕获 → 下游处理
  → 业务解耦；新增一个消费者不必改所有写路径
```

### 4.3 必须接受的代价

- **最终一致**：有秒级甚至更高延迟，不是强一致
- **链路更长**：MySQL、CDC、MQ、消费者都可能故障
- **至少一次投递**常见：必须幂等
- **乱序 / 回环**要设计（尤其多表、多实例）

---

## 5. 总体架构

### 5.1 推荐形态（生产常见）

```text
┌─────────────┐     binlog      ┌──────────────┐
│   MySQL     │ ──────────────► │ Canal /      │
│ (业务库)    │   dump / dump  │ Debezium     │
└─────────────┘                 └──────┬───────┘
                                       │ 解析后的行变更
                                       ▼
                                ┌──────────────┐
                                │ RabbitMQ /   │
                                │ Kafka        │
                                └──────┬───────┘
                                       │
                                       ▼
                                ┌──────────────┐
                                │ Spring Boot  │
                                │ @RabbitListener
                                │ / KafkaListener
                                │ 删缓存 / 写 ES
                                └──────────────┘
```

Spring Boot 的角色通常是 **下游消费者**，而不是自己假装从库去拉 Binlog（可以，但不推荐新手一上来嵌入式硬刚）。

### 5.2 简化形态（学习用）

```text
MySQL → Canal Server → Canal Client（嵌在 Spring Boot 里）→ 直接删缓存 / 打日志
```

少一层 MQ，方便本地跑通；上生产再加 MQ 做削峰与多订阅。

### 5.3 数据流时序

```text
1. 事务在 MySQL 提交，写入 Binlog
2. CDC 伪装成从库（或用协议）拉取并解析 ROW 事件
3. 按库表过滤，只保留 book / borrow_order 等
4. 发到 MQ（或直推 Client）
5. Spring Boot 消费：
   - UPDATE book id=1 → DEL redis key book:detail:1
   - INSERT/UPDATE book → ES index upsert
   - DELETE → ES delete + 删缓存
6. 成功则 ACK；失败重试 / 死信
```

---

## 6. MySQL 侧必备配置

学习 / 接入前至少确认：

```sql
-- 开启 binlog（my.cnf / 云控制台）
-- log_bin = ON
-- binlog_format = ROW
-- binlog_row_image = FULL   -- 建议：UPDATE 带齐前后列，CDC 更好用

SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'binlog_format';
SHOW VARIABLES LIKE 'binlog_row_image';
SELECT @@server_id;   -- 必须 > 0，Canal 等需要独特 server_id
```

权限（示意，按组件文档收紧）：

```sql
CREATE USER 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
```

注意：

- 云数据库常要单独开「逻辑复制 / Binlog」开关
- 磁盘与保留天数：`binlog_expire_logs_seconds`，避免位点过旧无法追
- 大事务、宽表 ROW 镜像会放大 Binlog 体积

---

## 7. 主流接入方式对比

| 方案 | Spring Boot 怎么接 | 优点 | 缺点 | 国内常见度 |
| --- | --- | --- | --- | --- |
| **Canal** | Canal Client 或 Canal → MQ → `@RabbitListener` | 中文资料多、阿里系成熟 | 要运维 Canal Server | 高 |
| **Debezium** | Kafka Connect + `@KafkaListener`；或 Embedded Engine | 生态标准、多库支持 | Kafka 链路重；版本要对齐 | 高（国际化） |
| Maxwell | 输出到 Kafka/HTTP | 轻量 | 社区体量相对小 | 中 |
| 自研读 Binlog | 直接依赖 `mysql-binlog-connector-java` | 可控 | 位点、DDL、HA 自己扛 | 学习向 |

本仓库已有 RabbitMQ：学习路径优先 **Canal → RabbitMQ → Spring Boot**，和现有消息体系一致。

---

## 8. Canal + Spring Boot 落地

### 8.1 组件分工

| 组件 | 职责 |
| --- | --- |
| Canal Server | 伪从库拉 Binlog、解析、存位点 |
| Canal Destination | 一个同步通道（可过滤正则库表） |
| Spring Boot | Client 拉取或订阅 MQ 中的变更消息 |

### 8.2 事件长什么样（逻辑结构）

Canal 解析后，一条业务相关消息大致包含：

```text
schema / database: smart_bookstore
table: book
eventType: INSERT | UPDATE | DELETE
pkNames: [id]
data: 变更后行（或插入行）
old: UPDATE/DELETE 前镜像（取决于配置与 row image）
es / gtids: 位点信息（便于排查）
```

Spring Boot 里先映射成自己的 DTO，再分支处理。

### 8.3 Client 直连（学习最小集）

思路（伪代码，非本仓库现码）：

```java
@Component
public class BookCanalListener {
    @PostConstruct
    public void start() {
        // 1. 连接 Canal Server
        // 2. subscribe("smart_bookstore", "book")
        // 3. 循环 getWithoutAck → 处理 → ack
    }

    void onEvent(CanalEvent e) {
        if (!"book".equals(e.table())) return;
        Long id = e.pkAsLong("id");
        switch (e.type()) {
            case DELETE -> cacheEvict(id);
            case INSERT, UPDATE -> {
                cacheEvict(id);
                // 可选：searchIndexer.upsert(id)
            }
        }
    }
}
```

要点：

- **单线程或按主键分区**处理同一行，降低乱序覆盖
- 处理成功再 ack，失败不要盲目 ack
- 应用关闭时优雅停 Client，避免位点乱跳

### 8.4 Canal → RabbitMQ → Spring Boot（更接近生产）

```text
Canal 投递到 Exchange
  → Queue: cache.evict / search.index
  → @RabbitListener 消费
```

本仓库已有确认、重试、死信实践（见消息队列分册），CDC 下游应复用同一套：

- 消费幂等
- 失败重试
- 死信告警
- 指标：延迟（事件时间 vs 消费时间）、失败次数

### 8.5 配置清单（学习自检）

- [ ] MySQL `ROW` + 账号权限
- [ ] Canal `instance.properties` 指向正确库
- [ ] 表过滤正则只含需要的表（如 `book`）
- [ ] Spring Boot 能打印第一条 INSERT/UPDATE
- [ ] 手动改一行书名，看到缓存被删或日志打出

---

## 9. Debezium + Spring Boot 落地

### 9.1 典型链路

```text
MySQL → Debezium MySQL Connector（Kafka Connect）
     → Kafka Topic（按表）
     → Spring Boot @KafkaListener
```

### 9.2 消息语义（概念）

Debezium 常用 envelope：

```text
op: c (create) / u (update) / d (delete) / r (read=快照)
before: 旧行
after: 新行
source: 库表、gtid、ts 等
```

Spring Boot 反序列化后同样做幂等处理。

### 9.3 Embedded Engine（无 Kafka 的学习路径）

Debezium 也可 **嵌在 JVM 进程**里跑 Engine，把事件回调给你的 Handler。适合 Demo，要注意：

- 与 Web 进程耦合、扩缩容麻烦
- 位点存储要可靠（文件 / DB）
- 生产更常见仍是 Connect + Kafka

与 Canal Client 直连类似：**能跑通原理，不等于生产首选形态。**

---

## 10. 消费端核心设计

这是面试与生产差距最大的一节。

### 10.1 幂等

CDC 几乎都是 **至少一次**：同一变更可能消费两次。

做法示例：

| 下游 | 幂等策略 |
| --- | --- |
| 删缓存 | `DEL` 多次无害（天然幂等） |
| 写 ES | 按 `bookId` upsert；或带版本字段 |
| 写派生表 | 唯一键 / `UPDATE … WHERE updated_at <= ?` |
| 有副作用通知 | 用 `gtid+row` 或业务幂等键去重表 |

### 10.2 乱序

主从切换、并发消费、重放都可能导致「先到的事件其实更旧」。

缓解：

- 同行变更 **分区键 = 主键**，保证同 id 有序
- 消息带 `update_time` / binlog 位点，乱序则丢弃旧事件
- 删缓存场景：多删一次往往可接受；写 ES 要用版本

### 10.3 只处理需要的表与列

```text
允许表: book, book_category
忽略: 验证码、会话、高频无关表
```

减少噪声与消费压力。库存高频更新若灌进 ES，要评估 QPS——可只同步 `title/status`，库存仍读 MySQL。

### 10.4 本地消息表 vs Binlog

| | 事务消息 / 本地表 | Binlog CDC |
| --- | --- | --- |
| 与业务事务 | 同库事务更贴 | 提交后可见，天然「之后」 |
| 侵入 | 要改写路径 | 写路径可不动 |
| 多服务写同表 | 难统一 | 强项 |

秒杀发券、订单超时本仓库已用 **MQ + 业务可靠投递**；CDC 更适合「旁路派生」而不是替代核心事务消息。

### 10.5 失败与积压

```text
消费失败 → 重试 → 死信队列 → 人工 / 自动对账
积压 → 扩容消费者；临时降级（搜索可稍旧、缓存多打 DB）
```

监控建议：

- Binlog 延迟（秒）
- 队列深度
- 消费失败率
- 对账差异数（定时 MySQL vs ES / 缓存抽样）

### 10.6 危险操作

- 在监听里再写回 **同一 MySQL 表** 且无防护 → 事件环（要过滤本系统标记或禁写）
- 把 CDC 当强一致事务 → 错误预期
- 全库订阅无过滤 → 打爆下游

---

## 11. 书城场景设计草案

### 11.1 场景 A：图书详情缓存失效（优先）

对应高一致性文档方案 E。

```text
book 表 INSERT/UPDATE/DELETE
  → CDC
  → MQ routingKey=catalog.book.changed
  → CacheEvictConsumer
       DEL book:detail:{id}
       （可选）布隆：删除无法从 Bloom 可靠移除，保持现有策略
```

与「Service 里主动 evict」二选一作 **主失效源**，避免双轨互相踩。

### 11.2 场景 B：同步 Elasticsearch（演进搜索时）

```text
book 变更 → Upsert/Delete ES documents
失败 → 重试；定时对账补漏
```

详情库存仍以 MySQL / Redis 为准；ES 可只存检索字段。

### 11.3 场景 C：不建议上 Binlog 的

| 场景 | 原因 |
| --- | --- |
| 秒杀扣库存 | 要事务内原子与强一致，已有 Redis Lua + DB |
| JWT / 会话 | 本就在 Redis，无需 Binlog |
| AI 对话记忆 | Redis Memory，非 MySQL 行变更主路径 |

### 11.4 模块切分建议（若落地）

```text
com.zx.cdc
  ├── config/          // 开关、表白名单
  ├── consumer/        // MQ 监听
  ├── handler/
  │     ├── BookCacheEvictHandler
  │     └── BookSearchIndexHandler
  └── support/         // 幂等、位点调试日志
```

用配置关闭：`cdc.enabled=false`，本地默认关，与 `ai.enabled` 同类。

---

## 12. 双写 vs Binlog 怎么选

| 维度 | 应用双写 | Binlog CDC |
| --- | --- | --- |
| 代码侵入 | 每个写路径都要记得 | 写路径干净 |
| 一致性 | 仍最终一致（第二下失败就要补偿） | 最终一致 |
| 多服务写同表 | 易漏 | 统一捕获 |
| 运维复杂度 | 低 | 较高 |
| 本仓库现阶段 | 改书后直接删缓存即可 | 体量小可暂缓 |
| 上 ES / 多下游时 | 双写变脏 | **更值得上** |

实用建议：

1. **现在**：缓存仍可应用内失效 + 文档吃透方案 E  
2. **引入 ES 或多服务写 catalog**：优先评估 Canal → MQ → Spring Boot  
3. **核心下单链路**：继续事务 + 业务 MQ，不要用 Binlog 替代

---

## 13. 常见坑与运维

| 坑 | 现象 | 处理 |
| --- | --- | --- |
| 未开 ROW | 解析不稳 / 工具拒绝 | `binlog_format=ROW` |
| `binlog_row_image=MINIMAL` | UPDATE 缺列 | 改 `FULL`（评估体积） |
| 位点过旧 | Canal 起不来 | 留足保留期；必要时重置并从全量快照重建 |
| 只 ack 不处理成功 | 丢事件 | 先处理成功再 ack |
| 无幂等 | 重复删还好；重复发短信灾难 | 按下游设计幂等 |
| DDL 变更 | 解析失败 / 字段对不上 | 关注组件 DDL 支持；消费代码兼容新列 |
| 时区 | 时间字段偏移 | 统一 UTC 或明确会话时区 |
| 权限不足 | 连不上 / 无 dump | `REPLICATION SLAVE` 等 |
| 把测试库订阅进生产消费者 | 脏数据 | 严格库表白名单与环境隔离 |

本地学习可用 Docker Compose：**MySQL（开 binlog）+ Canal + RabbitMQ + 本机 Spring Boot**。不必一上来上高可用 Canal 集群。

---

## 14. 分阶段学习路线

### B1：Binlog 本身（1～2 天）

- 读懂格式、用途、与 redo 区别
- 本地 MySQL 打开 Binlog，用 `mysqlbinlog` 看一条 UPDATE（有环境的话）
- 产出：一页笔记「Binlog 是什么」

### B2：跑通 Canal 最小闭环（3～5 天）

- Docker 起 MySQL + Canal
- Spring Boot 用 Client 或控制台看 `book` 表变更
- 手动 `UPDATE` 一行，日志打印 pk 与类型
- 产出：截图 / 日志证明链路通

### B3：接 MQ + 幂等（3～5 天）

- Canal → RabbitMQ → `@RabbitListener`
- 实现「变更 → 删一个假缓存 Key」
- 故意重复投递，验证幂等
- 产出：与本仓库 MQ 重试 / 死信对照说明

### B4：对标本仓库设计（2～3 天）

- 对照 [高一致性方案 E](../数据库与缓存高一致性业务场景实现方案.md)
- 写一页「书城是否上 Canal」决策：现在不上的理由 + 将来上的触发条件
- 口述 ES 同步为何更适合 Binlog 而非双写

### B5：选学 Debezium（可选）

- 同一套消费语义换 Kafka envelope
- 对比 Canal 与 Debezium 运维差异

---

## 15. 能力自检与面试口述

### 15.1 自检清单

- [ ] 能说明 Spring Boot 并不直接「内置 Binlog」，而是消费 CDC 事件
- [ ] 能画出 MySQL → Canal/Debezium → MQ → 应用
- [ ] 知道为何 CDC 要用 `ROW`
- [ ] 能讲位点 / GTID 是干什么的
- [ ] 消费端有幂等与失败重试思路
- [ ] 能区分：核心事务消息 vs 旁路 CDC
- [ ] 能结合书城说缓存失效与 ES 同步场景

### 15.2 推荐口述（约 2 分钟）

> Spring Boot 本身不读 Binlog，而是接 CDC。MySQL 在事务提交后写 ROW 格式 Binlog，Canal 或 Debezium 伪装成从库解析出行级变更，再丢到 MQ。我们的应用只负责消费：比如 book 表变更就删详情缓存，或 upsert 到 ES。这样业务服务继续只写数据库，多下游互不影响。要接受最终一致，并且按至少一次投递做幂等；位点由 Canal/Kafka 维护，消费者用死信和定时对账兜底。书城现在体量小、写路径集中，缓存可以先应用内失效；一旦上全文检索或多服务写书目表，再上 Binlog 同步更划算。

### 15.3 可能被追问

1. Binlog 会不会丢？ → 配置与磁盘、提交机制有关；CDC 还要防消费者自己丢（ack 时机）。  
2. 和 MQ 事务消息啥区别？ → 事务消息服务业务副作用；CDC 服务「库已变更」的旁路派生。  
3. 延迟多大？ → 正常亚秒到数秒；看 CDC 与队列积压。  
4. 能保证顺序吗？ → 单行可分区保序；全局顺序别指望。  
5. 会不会环？ → 监听又写回同源表要小心，需隔离或标记。

---

## 相关文档

- [Elasticsearch 学习文档](./Elasticsearch学习文档.md)（同步方案中的 Binlog）
- [数据库与缓存高一致性业务场景实现方案](../数据库与缓存高一致性业务场景实现方案.md)（方案 E：Canal 删缓存）
- [消息队列](./消息队列.md)
- [消息模型与投递语义](./消息模型与投递语义.md)
- [缓存](./缓存.md)
- [RabbitMQ 使用指南学习文档](../RabbitMQ使用指南学习文档.md)

---

## 一句话总结

**Spring Boot「使用 Binlog」= 让 CDC 把 MySQL 变更变成消息，应用做幂等的旁路处理（删缓存、同步 ES）；业务真相仍只写 MySQL，接受最终一致，并用 MQ 重试与对账托底。**
