# 加深方向学习路线

本目录对应 [后续学习路线](../后续学习路线.md) 中「把现有亮点讲透」：每篇绑定 **smart_bookstore** 里已有或可演进的能力，建议边读边打开对应源码。

入口总索引见 [docs/README.md](../README.md)。

---

## 建议顺序

1. 语言与运行时打底（可选）：[Java 基础复习大纲](./Java基础复习大纲.md) → [JVM 与内存模型](./JVM与内存模型：从运行时区域到类加载.md) → [Java 并发编程实战](./Java并发编程实战：用代码把原理跑通.md)
2. 项目进阶主线二选一：
   - **高并发 + 缓存 + MQ**（书城 / 秒杀 / 借阅）
   - **AI 工程**（`com.zx.ai`）
3. 中间件 / 长连接方向：先 [I/O 模型手写实现](./IO模型手写实现.md) → [计算机 I/O 模型介绍学习文档](../计算机IO模型介绍学习文档.md) → [Netty 前置基础](./Netty前置基础.md)

---

## 分册索引

| 文档 | 核心主题 | 关联本仓库 |
| --- | --- | --- |
| [Java 基础复习大纲](./Java基础复习大纲.md) | 语言 / OOP / 集合 / 并发 / JVM | 通用基础 |
| [JVM 与内存模型：从运行时区域到类加载](./JVM与内存模型：从运行时区域到类加载.md) | 运行时区域、类加载 | 通用基础 |
| [Java 并发编程实战：用代码把原理跑通](./Java并发编程实战：用代码把原理跑通.md) | 线程、锁、JUC | 预约 / 秒杀并发背景 |
| [Netty 前置基础](./Netty前置基础.md) | 网络 / NIO / Reactor | 进阶路线 B |
| [I/O 模型手写实现](./IO模型手写实现.md) | BIO / Selector / Reactor 骨架 | 进阶路线 B |
| [高并发](./高并发.md) | 库存、超卖、削峰、延迟队列 | `borrow` / `trade` / `seckill` |
| [秒杀令牌桶限流](./秒杀令牌桶限流.md) | grab 入口 Redis Lua 令牌桶 | `SeckillRateLimitService` + `seckill_token_bucket.lua` |
| [生产可用令牌桶限流学习文档](../生产可用令牌桶限流学习文档.md) | 原理、完整 Lua、调参与坑 | 同上 |
| [缓存](./缓存.md) | 穿透 / 击穿 / 雪崩、布隆、一致 | `bookstore.catalog` 缓存链路 |
| [布隆过滤器](./布隆过滤器.md) | `m` / `k`、Redis Bitmap、预热 | `BookBloomRedisService` |
| [bloom-mk-calculator.html](./bloom-mk-calculator.html) | `m`/`k` 计算器 | 配合布隆篇 |
| [缓存增强分步实现指南](./缓存增强分步实现指南.md) | Caffeine + Redis、热点 TTL | `BookstoreCacheProperties` |
| [图书缓存业务指标分步实现指南](./图书缓存业务指标分步实现指南.md) | Counter / Timer 与 Prometheus | Actuator 指标 |
| [生产可用逻辑过期缓存学习文档](../生产可用逻辑过期缓存学习文档.md) | 逻辑过期 + 互斥锁 + 异步重建 | `LogicalExpireCacheClient` |
| [消息队列](./消息队列.md) | 可靠投递、死信、幂等消费 | `seckill` / `trade` MQ |
| [消息模型与投递语义](./消息模型与投递语义.md) | Exchange / Queue、Confirm / ACK | RabbitMQ 配置与 Consumer |
| [AI 工程](./AI工程.md) | Prompt / Tool 评测、RAG、成本 | `com.zx.ai` |

项目内对应的「入门 → 实战」学习文档见上级目录 [认证](../登录流程学习文档.md)、[缓存三种方案](../Redis缓存查询三种方案学习文档.md)、[秒杀 P4](../P4秒杀业务流程与技术栈.md)、[AI 模块](../AI模块学习文档.md) 等。
