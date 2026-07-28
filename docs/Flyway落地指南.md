# Flyway 落地指南（本项目）


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联**：`db/migration` — 表结构版本化；对照 V1__init_schema.sql  
> **索引**：[学习文档中心](./README.md)

把「手跑 `schema.sql` + `migration-*.sql`」改成 **启动时自动、可追溯的版本化迁移**。


---

## 1. 目标与非目标

### 目标

- 空库启动：自动建齐当前表结构
- 已有库：对齐版本号，后续只跑增量脚本
- 多人 / 多环境：靠 `flyway_schema_history` 确认「库跑到哪一版」
- 关闭 `spring.sql.init`，避免与 Flyway 双通道打架

### 非目标

- 不把历史 `migration-*.sql` 全部还原成 V2、V3…（多数已并入 V1）
- 社区版不做自动 down 迁移

---

## 2. 当前仓库状态（已接入）

| 项 | 状态 |
| --- | --- |
| 依赖 | `spring-boot-starter-flyway` + `flyway-mysql` |
| 基线脚本 | `src/main/resources/db/migration/V1__init_schema.sql` |
| 旧脚本 | `src/main/resources/db/archive/`（不参与扫描） |
| `spring.sql.init.mode` | `never` |
| `spring.flyway.baseline-on-migrate` | `true`（空库跑 V1；已有库无历史表则 baseline=1） |

```text
启动 → Flyway 查 flyway_schema_history → 只执行未跑过的版本脚本
```

---

## 3. 采用的策略：基线合并（策略 A）

1. 当前完整 schema → `V1__init_schema.sql`
2. 旧 `migration-*.sql` / 旧 `schema.sql` → `db/archive/`
3. 以后表结构变更只新增 `V2__xxx.sql`、`V3__xxx.sql`…

归档对照（不必再跑）：

| 归档文件 | 已并入 V1 的大致内容 |
| --- | --- |
| `migration-seat.sql` | `reservation_seat`、订单座位字段 |
| `migration-borrow.sql` | `borrow_order` |
| `migration-borrow-overdue.sql` | `(status, due_at)` 索引 |
| `migration-book-shelf.sql` | `bookshelf`、图书位置字段 |
| `migration-book-stock-log.sql` | `book_stock_log` |
| `migration-checkin.sql` | 余额、签到表、优惠券相关 |
| `migration-seckill.sql` | `seckill_activity` / `seckill_order` |
| `migration-trade-timeout-fail.sql` | `trade_order_timeout_fail` |

---

## 4. 日常使用

### 4.1 空库

```sql
CREATE DATABASE IF NOT EXISTS smart_bookstore
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

激活 `local` profile 启动应用即可；Flyway 会创建历史表并执行 V1。

### 4.2 已有库（表已齐，首次接 Flyway）

当前配置已开启 `baseline-on-migrate`：无历史表时记 version=`1`，**不重复执行 V1**。确认后可查：

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

稳定后若希望禁止自动 baseline，可将 `spring.flyway.baseline-on-migrate` 改为 `false`。

### 4.3 以后怎么加变更

1. 新建 `V2__add_foo.sql`，只写增量
2. 启动 → 自动只跑未执行版本
3. **禁止**修改已合并、别人已执行过的 `V1` / `V2` 内容
4. 开发库改坏 checksum：重建库或 `flyway repair`；生产用新 `Vn` 补救

命名规则：

| 类型 | 格式 | 示例 |
| --- | --- | --- |
| 版本化（只跑一次） | `V{版本}__{描述}.sql` | `V2__add_xxx.sql` |
| 可重复 | `R__{描述}.sql` | `R__seed_dev_books.sql` |

注意：两个下划线 `__`。

---

## 5. 配置摘录

```yaml
spring:
  sql:
    init:
      mode: never
  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8
    baseline-on-migrate: true
    baseline-version: 1
    baseline-description: existing_schema
    validate-on-migrate: true
```

单元测试（`application-test.yaml`）默认 `spring.flyway.enabled=false`；Testcontainers 集成测应开启并让空库跑迁移。

---

## 6. 种子数据

`archive/seed-test-books.sql` 未并入 V1（V1 已含基础示例书）。更多演示数据可选：

| 方式 | 做法 |
| --- | --- |
| 可重复迁移 | `R__seed_dev_books.sql`，幂等 `INSERT … WHERE NOT EXISTS` |
| 手工 / local Runner | 仅本地灌数 |

---

## 7. 常见坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Unsupported Database: MySQL 8.0` | 缺 `flyway-mysql` | 已在 pom 引入 |
| 加了 `flyway-core` 但不跑迁移 | Boot 4 需 starter | 使用 `spring-boot-starter-flyway` |
| 启动既跑 schema 又跑 Flyway | `sql.init` 未关 | 保持 `mode: never` |
| `Migration checksum mismatch` | 改过已执行脚本 | 开发库 repair / 重建；生产发新 Vn |
| `Table already exists` | 已有库又跑了 V1 | 依赖 baseline，或清空后重来 |
| 脚本里有 `USE other_db` | 与数据源不一致 | V1 已去掉 `USE` |

---

## 8. 回滚

社区版无完善自动 down。实践：向前发 `V{n+1}__fix_xxx.sql`；严重错误从备份恢复并对齐历史表。勿在生产使用 `flyway clean`。

---

## 9. 验收清单

- [x] `pom.xml` 有 `spring-boot-starter-flyway` + `flyway-mysql`
- [x] 脚本在 `classpath:db/migration`，命名为 `V1__…`
- [x] `spring.sql.init.mode=never`
- [x] 旧 `migration-*.sql` / `schema.sql` 已归档
- [x] README「数据库初始化」已改为 Flyway 说明
- [ ] 空库或已有库启动一次，确认 `flyway_schema_history`
- [ ] 下一张表变更用 `V2__…` 验证增量路径

---

## 10. 相关路径

| 路径 | 用途 |
| --- | --- |
| `src/main/resources/db/migration/V1__init_schema.sql` | 当前基线 |
| `src/main/resources/db/archive/` | 历史手工脚本 |
| `src/main/resources/application.yaml` | `flyway.*` / `sql.init` |
| `pom.xml` | Flyway 依赖 |

官方参考：

- [Spring Boot Flyway](https://docs.spring.io/spring-boot/reference/howto/data-initialization.html#howto.data-initialization.migration-tool.flyway)
- [Flyway Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
