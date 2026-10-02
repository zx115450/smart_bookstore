# 加深方向学习路线

本目录对应 [后续学习路线](../后续学习路线.md) 中「把现有亮点讲透」：每篇绑定 **smart_bookstore** 里已有或可演进的能力，建议边读边打开对应源码。

入口总索引见 [docs/README.md](../README.md)。

---

## 建议顺序

1. 语言与运行时打底（可选）：[Java 基础复习大纲](./Java基础复习大纲.md) → [Java 程序如何运行：静态成员与类加载](./Java程序如何运行：静态成员与类加载.md) → [JVM 与内存模型](./JVM与内存模型：从运行时区域到类加载.md) → [Java 并发编程实战](./Java并发编程实战：用代码把原理跑通.md)
2. 项目进阶主线二选一：
   - **高并发 + 缓存 + MQ**（书城 / 秒杀 / 借阅）
   - **AI 工程**（`com.zx.ai`）→ 可选加深 [Coding Agent 学习路线](./CodingAgent学习路线.md)（类 Claude Code 的改代码 Agent）
3. 中间件 / 长连接方向：先 [I/O 模型手写实现](./IO模型手写实现.md) → [计算机 I/O 模型介绍学习文档](../计算机IO模型介绍学习文档.md) → [Netty 前置基础](./Netty前置基础.md)
4. 搜索演进（可选）：当前书目为 MySQL `LIKE`，全文检索加深见 [Elasticsearch 学习文档](./Elasticsearch学习文档.md)
5. 数据变更捕获（可选）：[Spring Boot 使用 Binlog 学习文档](./SpringBoot-Binlog学习文档.md)（Canal / Debezium → 删缓存 / 同步 ES）

---

## 分册索引

| 文档 | 核心主题 | 关联本仓库 |
| --- | --- | --- |
| [Java 基础复习大纲](./Java基础复习大纲.md) | 语言 / OOP / 集合 / 并发 / JVM | 通用基础 |
| [Java 程序如何运行：静态成员与类加载](./Java程序如何运行：静态成员与类加载.md) | 启动链路、`static`、静态内部类加载 | 通用基础；衔接 Boot 自动配置 |
| [JVM 与内存模型：从运行时区域到类加载](./JVM与内存模型：从运行时区域到类加载.md) | 运行时区域、类加载 | 通用基础 |
| [Java 并发编程实战：用代码把原理跑通](./Java并发编程实战：用代码把原理跑通.md) | 线程、锁、JUC | 预约 / 秒杀并发背景 |
| [Netty 前置基础](./Netty前置基础.md) | 网络 / NIO / Reactor | 进阶路线 B |
| [I/O 模型手写实现](./IO模型手写实现.md) | BIO / Selector / Reactor 骨架 | 进阶路线 B |
| [高并发](./高并发.md) | 库存、超卖、削峰、延迟队列 | `borrow` / `trade` / `seckill` |
| [秒杀令牌桶限流](./秒杀令牌桶限流.md) | grab 入口 Redis Lua 令牌桶 | `SeckillRateLimitService` + `seckill_token_bucket.lua` |
| [生产可用令牌桶限流学习文档](../生产可用令牌桶限流学习文档.md) | 原理、完整 Lua、调参与坑 | 同上 |
| [缓存](./缓存.md) | 穿透 / 击穿 / 雪崩、布隆、一致 | `bookstore.catalog` 缓存链路 |
| [Redis 动态拓扑接入学习文档](./Redis动态拓扑接入学习文档.md) | 单体 / 主从 / 哨兵 / 分片集群动态切换 | `com.zx.config.redis` |
| [布隆过滤器](./布隆过滤器.md) | `m` / `k`、Redis Bitmap、预热 | `BookBloomRedisService` |
| [bloom-mk-calculator.html](./bloom-mk-calculator.html) | `m`/`k` 计算器 | 配合布隆篇 |
| [缓存增强分步实现指南](./缓存增强分步实现指南.md) | Caffeine + Redis、热点 TTL | `BookstoreCacheProperties` |
| [热点 Key 加长缓存 TTL 学习文档](../热点Key加长缓存TTL学习文档.md) | 通用实战：分桶窗口 + 热点长 TTL | — |
| [图书缓存业务指标分步实现指南](./图书缓存业务指标分步实现指南.md) | Counter / Timer 与 Prometheus | Actuator 指标 |
| [生产可用逻辑过期缓存学习文档](../生产可用逻辑过期缓存学习文档.md) | 逻辑过期 + 互斥锁 + 异步重建 | `LogicalExpireCacheClient` |
| [消息队列](./消息队列.md) | 可靠投递、死信、幂等消费 | `seckill` / `trade` MQ |
| [消息模型与投递语义](./消息模型与投递语义.md) | Exchange / Queue、Confirm / ACK | RabbitMQ 配置与 Consumer |
| [MQ 本地重试与 Spring Template](./MQ本地重试与Spring-Template学习文档.md) | Retry 队列（默认）/ 本地 `RetryTemplate`、`*Template` | `com.zx.config.mq` + `*.retry` 队列 |
| [AI 工程](./AI工程.md) | Prompt / Tool 评测、RAG、成本 | `com.zx.ai` |
| [Coding Agent 学习路线](./CodingAgent学习路线.md) | Agent Loop、读写搜改、Diff、流式 UX、沙箱权限 | 对标 Claude Code；衔接 Study Agent / Spring AI |
| [Spring AI 常用类学习文档](./SpringAI常用类学习文档.md) | ChatClient / Advisor / Tool / Memory / VectorStore 类辞典与加载机制 | `com.zx.ai.config` 等 |
| [Elasticsearch 学习文档](./Elasticsearch学习文档.md) | 倒排、Mapping、Query DSL、与 MySQL 同步 | 书目搜索演进（现状 `LIKE`） |
| [Spring Boot 使用 Binlog 学习文档](./SpringBoot-Binlog学习文档.md) | CDC、Canal / Debezium、消费幂等 | 缓存失效 / ES 同步演进 |
| [Oracle 使用文档](./Oracle使用文档.md) | 架构、用户/表空间、SQL、过程与触发器、工具 | 通用基础（本仓库运行时以 MySQL 为主） |
| [Java 使用 Oracle 文档](./Java使用Oracle文档.md) | JDBC / Spring Boot / MyBatis 接 Oracle、类型与分页差异 | 通用基础；对照本仓库 MySQL 接入 |
| [MySQL 与 Oracle 保证 CP 面试文档](./MySQL与Oracle保证CP面试文档.md) | CAP、半同步/GR、RAC/Data Guard、口述稿 | 面试加深；衔接 Oracle 使用文档 |

项目内对应的「入门 → 实战」学习文档见上级目录 [认证](../登录流程学习文档.md)、[缓存三种方案](../Redis缓存查询三种方案学习文档.md)、[秒杀 P4](../P4秒杀业务流程与技术栈.md)、[AI 模块](../AI模块学习文档.md) 等。
