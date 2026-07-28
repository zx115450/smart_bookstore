# 项目可完善内容清单

本文档基于当前代码结构、配置与测试现状，整理出 smart_bookstore 项目可以继续完善的方向。按优先级分为 P0（建议优先）、P1（中期）、P2（长期/体验）。每个条目包含问题描述、影响范围和推荐方案，便于逐步推进。

---

## 优先级说明

- **P0**：影响安全、稳定性或核心功能，建议尽快处理。
- **P1**：影响工程质量、可维护性或可观测性，建议在主功能稳定后补齐。
- **P2**：提升体验、扩展能力，可按需求排期。

---

## P0 - 高优先级

### 1. 敏感信息与默认密钥清理

**问题**：
`application.yaml` 中曾包含明文凭据和默认密钥（邮件、数据库、JWT 等）。

**状态**：敏感项已抽离为环境变量占位；本地通过 `application-local.yaml`（gitignore）+ `spring.profiles.active=local` 注入。模板见 `application-local.yaml.example`。

**仍须注意**：

1. 生产环境强制通过环境变量或密钥管理服务注入，禁止把真实密钥写回 `application.yaml`。
2. 若密钥曾提交到 Git 历史，应轮换 SMTP / JWT / 数据库密码。
3. 启动必须带 `local` profile，或自行注入 `DB_*` / `MAIL_*` / `JWT_SECRET` 等变量。

**参考**：

- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yaml.example`

---

### 2. 补充核心测试覆盖

**问题**：
测试目录只有一个几乎为空的测试类：

```java
// src/test/java/com/zx/SmartBookstoreApplicationTests.java
@Test
void contextLoads() {
    List<Integer> list = List.of(1 , 2 , 3, 4, 5, 56);
    // ... 无实际业务断言
}
```

**影响**：
核心业务流程（登录、下单、借阅、预约、秒杀、AI 对话）均无自动化测试，重构风险高，回归成本高。

**建议**：

1. 按模块补充单元测试：
   - `AuthService` 登录、刷新、登出逻辑。
   - `BookCatalogService` 图书检索与库存变更。
   - `BorrowService` 借阅状态机。
   - `TradeService` 订单创建与优惠券核销。
   - `ReservationService` 预约与座位冲突。
   - `SeckillService` 秒杀库存扣减。
2. 使用 `@SpringBootTest` + `Testcontainers` 做 MySQL / Redis / RabbitMQ 集成测试。
3. 对 AI 工具层使用 mock `ChatClient` 验证 Tool 调用路径。

**参考**：

- `src/test/java/com/zx/SmartBookstoreApplicationTests.java`

---

### 3. 统一异常处理与错误码

**问题**：

- 每个业务模块都有自己的 `ExceptionHandler`，共 9 个：
  - `CartExceptionHandler`
  - `BorrowExceptionHandler`
  - `SeckillExceptionHandler`
  - `TradeExceptionHandler`
  - `CheckinExceptionHandler`
  - `BookstoreExceptionHandler`
  - `ReservationExceptionHandler`
  - `CouponExceptionHandler`
  - `AiExceptionHandler`
- `AiExceptionHandler` 仅扫描 `com.zx.ai.controller` 包，但 `AiException` 可能在 Service 层抛出。
- `AiException` 注释中提到错误码与 QQ OAuth 的 `5001` 可能重叠。

**影响**：
错误码和响应格式分散，前端需针对多个模块做不同处理；异常可能在某些路径下未被捕获。

**建议**：

1. 建立统一的 `GlobalExceptionHandler` 兜底，并保留各模块的特异性处理。
2. 统一错误码区间：
   - `1000`：认证
   - `2000`：授权/会话
   - `3000`：书城
   - `4000`：预约/签到
   - `5000`：AI
3. 将 `ApiResponse` 抽到一个独立的 `common` 或 `shared` 包，避免循环依赖。

**参考**：

- `src/main/java/com/zx/ai/exception/AiExceptionHandler.java:10`
- `src/main/java/com/zx/ai/exception/AiException.java:6`
- 各模块 `*ExceptionHandler.java`

---

### 4. AI 对话限流实现

**问题**：
`application.yaml` 中配置了 `ai.rate-limit.per-user-hourly: 30`，`AiProperties` 也提供了该字段，但代码中未看到针对 AI 聊天接口的实际限流逻辑。

**影响**：
匿名聊天接口为 `permitAll`，易被恶意调用导致 API 费用激增或上游模型限流。

**建议**：

1. 在 `AiChatController` 或 `AiChatService` 入口增加 Redis 滑动窗口限流，键可按用户 ID 或 IP 区分。
2. 对匿名用户按 IP 限流，对登录用户按 userId 限流。
3. 触发限流时返回 `AiException.rateLimited()`（错误码 5002）。

**参考**：

- `src/main/java/com/zx/ai/config/AiProperties.java:76`
- `src/main/java/com/zx/ai/service/AiChatService.java:43`
- `src/main/resources/application.yaml:111`

---

### 5. 数据库迁移版本化管理

**状态**：已接入 Flyway（策略 A）。

- 依赖：`spring-boot-starter-flyway` + `flyway-mysql`
- 基线：`src/main/resources/db/migration/V1__init_schema.sql`
- `spring.sql.init.mode=never`；`baseline-on-migrate=true`（兼容已有本地库）

后续表结构变更请新增 `V2__….sql`。说明见 [docs/Flyway落地指南.md](docs/Flyway落地指南.md)。

---
## P1 - 中优先级

### 6. 接入 Actuator 与可观测性

**问题**：
当前没有 `spring-boot-starter-actuator` 依赖，也没有健康检查、指标暴露或日志追踪机制。

**影响**：
部署后无法判断应用健康状态；无法及时发现数据库连接池、Redis、RabbitMQ、Milvus 等依赖异常。

**建议**：

1. 引入 `spring-boot-starter-actuator`。
2. 暴露 `/actuator/health`、`/actuator/info`、`/actuator/metrics`。
3. 自定义 Health Indicator 检查 MySQL、Redis、RabbitMQ、Milvus。
4. 引入 Micrometer + Prometheus 导出 JVM、HTTP、数据库连接池指标。
5. 接入日志 traceId（MDC），统一请求上下文。

**参考**：

- `pom.xml`（当前无 actuator 依赖）

---

### 7. 高并发场景引入分布式锁

**问题**：
秒杀、抢座、预约、借阅、下单等场景依赖乐观锁或数据库锁，但在高并发下可能出现：

- 秒杀库存超卖。
- 预约座位重复。
- 优惠券重复发放。

**影响**：
并发量上升时会产生数据不一致，需要兜底对账。

**建议**：

1. 引入 **Redisson** 分布式锁。
2. 在秒杀扣减库存、预约占座、领取优惠券等关键路径加锁。
3. 锁粒度尽量小，按资源 ID 拆分，避免全局锁。
4. 配合 Lua 脚本或 Redis 原子操作实现快速路径。

**参考**：

- `src/main/java/com/zx/bookstore/seckill/service/SeckillService.java`
- `src/main/java/com/zx/reservation/service/ReservationService.java`
- `src/main/resources/lua/borrow_due_pop.lua`

---

### 8. 消息消费幂等保证

**问题**：
RabbitMQ 消费者（如订单超时、秒杀异步处理）收到消息后，若处理失败或网络抖动，可能重复消费。

**影响**：
订单可能被重复取消、优惠券被重复发放。

**建议**：

1. 所有消息消费端使用 `idempotency_key` 或业务唯一键做幂等。
2. 在数据库中记录消息处理状态表（`message_consumed`）。
3. 使用 `manual` ACK 模式（已配置），消费成功后确认。
4. 死信队列处理超过重试次数的消息。

**参考**：

- `src/main/java/com/zx/bookstore/trade/consumer/TradeOrderTimeoutConsumer.java`
- `src/main/java/com/zx/bookstore/seckill/consumer/SeckillDeadLetterConsumer.java`
- `src/main/resources/application.yaml:65`

---

### 9. 流式响应与模型重试

**问题**：

- `AiChatService` 使用阻塞式 `call()`，等待完整响应后返回，前端等待时间长。
- 模型调用失败时直接返回错误，没有重试机制。

**影响**：
长文本生成时用户体验差；偶发模型超时会导致全部失败。

**建议**：

1. 支持 `stream()` 返回 SSE（Server-Sent Events），让前端逐步展示回复。
2. 对模型调用增加指数退避重试（如 3 次），仅对可重试异常（超时、5xx）重试。
3. 重试失败后降级到固定提示：「AI 服务暂时不可用，请稍后重试」。

**参考**：

- `src/main/java/com/zx/ai/service/AiChatService.java:64`

---

### 10. Redis 大 key 与阻塞风险

**问题**：
`RedisStringChatMemoryRepository.findConversationIds()` 使用 `redis.keys()`：

```java
Set<String> keys = redis.keys(KEY_PREFIX + "*");
```

**影响**：
会话量增大时，`KEYS` 命令会阻塞 Redis 单线程，影响所有业务缓存。

**建议**：

1. 改用 `SCAN` 分批扫描。
2. 或维护一个固定集合（如 `ai:chat:sessions`），记录活跃会话，避免全量扫描。

**参考**：

- `src/main/java/com/zx/ai/memory/RedisStringChatMemoryRepository.java:47`

---

### 11. 配置分层与环境隔离

**问题**：
当前只有一份 `application.yaml`，没有按环境拆分的 `application-dev.yaml`、`application-test.yaml`、`application-prod.yaml`。

**影响**：
不同环境需要反复修改同一文件，容易误改生产配置。

**建议**：

1. 拆分环境配置：
   - `application-dev.yaml`：本地开发。
   - `application-test.yaml`：测试/CI。
   - `application-prod.yaml`：生产。
2. 通用配置保留在 `application.yaml`，环境差异通过 `spring.profiles.active` 覆盖。
3. 敏感配置仍走 `application-local.yaml` 或环境变量。

**参考**：

- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yaml.example`

---

## P2 - 低优先级 / 体验优化

### 12. API 文档与 OpenAPI

**问题**：
没有自动生成 API 文档，新成员上手成本高。

**建议**：

1. 引入 `springdoc-openapi`。
2. 为 Controller 接口补充 `@Operation`、`@Parameter` 注解。
3. 暴露 Swagger UI：`http://localhost:8081/swagger-ui.html`。

---

### 13. CI/CD 流水线

**问题**：
项目没有 GitHub Actions / GitLab CI 配置。

**建议**：

1. 添加 Maven 编译、测试、打包流程。
2. 集成 Checkstyle 或 Spotless 代码格式检查。
3. 集成 OWASP Dependency-Check 安全扫描。
4. 构建 Docker 镜像并推送。

---

### 14. 支付与对账能力

**问题**：
当前仅使用余额模拟支付，没有真实支付渠道。

**建议**：

1. 预留支付接口，接入微信/支付宝/Stripe SDK。
2. 引入支付订单表与对账任务。
3. 使用分布式事务（如 Seata 或本地消息表）保证支付与订单状态一致。

---

### 15. 代码清理与细节优化

**问题**：

- `SmartBookstoreApplicationTests` 中引入了未使用的 `BCryptPasswordEncoder`。
- 主类 `SmartBookstoreApplication` 手动 import 大量 `@EnableConfigurationProperties` 配置类，可改为在配置类上加 `@ConfigurationProperties` + `@Component` 或 `@ConfigurationPropertiesScan`。
- 部分模块的 `ExceptionHandler` 重复捕获 `IllegalArgumentException`，可统一。

**建议**：

1. 删除无用 import。
2. 使用 `@ConfigurationPropertiesScan` 自动扫描配置类。
3. 统一基础参数校验异常处理。

**参考**：

- `src/test/java/com/zx/SmartBookstoreApplicationTests.java:5`
- `src/main/java/com/zx/SmartBookstoreApplication.java:20`

---

## 推荐推进顺序

1. **立即**：P0-1 密钥清理、P0-2 核心测试。
2. **1-2 周**：P0-3 统一异常、P0-4 AI 限流、P0-5 数据库迁移。
3. **1 个月内**：P1-6 Actuator、P1-7 分布式锁、P1-8 消息幂等。
4. **后续**：P1-9 流式响应、P1-10 Redis SCAN、P2 体验优化。

---

**维护提示**：本文档随项目演进更新。完成某项后，建议在对应条目前打勾或移动到「已完成」章节，避免清单与实际代码脱节。
