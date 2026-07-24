# Factory Test Demo - 智慧书城

一个基于 Spring Boot 4.1 + Spring AI 2.0 构建的「智慧书城」综合 Demo，涵盖 AI 客服、图书商城、借阅、预约、签到、秒杀、优惠券、RBAC 认证等模块。项目用于展示现代 Java 后端技术栈在书店/图书馆场景下的完整实践能力。

---

## 目录

- [技术栈](#技术栈)
- [模块说明](#模块说明)
- [环境依赖](#环境依赖)
- [快速开始](#快速开始)
- [配置文件说明](#配置文件说明)
- [数据库初始化](#数据库初始化)
- [主要 API](#主要-api)
- [AI 客服模块](#ai-客服模块)
- [测试](#测试)
- [部署建议](#部署建议)
- [补充文档](#补充文档)
- [安全提示](#安全提示)
- [许可证](#许可证)

---

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 基础框架 | Spring Boot 4.1.0、Java 21 |
| Web 与安全 | Spring Web MVC、Spring Security、JWT、BCrypt |
| 数据层 | MyBatis-Plus 3.5.9、MySQL 8+、HikariCP |
| 缓存与消息 | Redis、RabbitMQ |
| AI 与向量 | Spring AI 2.0.0、OpenAI 兼容协议（DeepSeek / 通义千问）、Milvus |
| 工具链 | Lombok、Maven、Jackson |

---

## 模块说明

项目按业务域划分为多个包，每个模块内采用分层结构（`controller / service / repository / dto / exception`）。

### 1. `com.zx.ai` - AI 客服模块

- 基于 Spring AI `ChatClient` + Tool Calling 实现对话。
- 提供查书、FAQ、个人借阅、推荐等工具（Tool），大模型只负责理解意图并组织回复。
- 多轮对话记忆存储在 Redis，通过 `MessageChatMemoryAdvisor` 自动管理。
- 可选 RAG（Milvus）做语义召回，书目与 FAQ 向量索引由管理端接口重建。

关键入口：

- `POST /api/ai/chat`：用户对话（匿名或登录均可）。
- `POST /api/ai/admin/reindex`：管理员重建书目向量索引。
- `POST /api/ai/admin/reindex-faq`：管理员重建 FAQ 向量索引。

### 2. `com.zx.auth` - 认证与权限

- 支持邮箱验证码登录、密码登录、QQ OAuth 登录。
- 使用 JWT Access Token + Refresh Token 双令牌机制，Refresh Token 持久化到 MySQL。
- 支持角色权限（`USER`、`ADMIN`），基于 Spring Security 方法级注解控制。
- 包含登录审计、验证码频率限制、会话黑名单等安全机制。

关键入口：

- `POST /api/auth/code/send`：发送邮箱验证码。
- `POST /api/auth/login`：登录（邮箱验证码 / 密码 / QQ OAuth）。
- `POST /api/auth/refresh`：刷新访问令牌。
- `POST /api/auth/logout`：退出登录。

### 3. `com.zx.bookstore` - 书城与借阅

包含以下子模块：

- `catalog`：图书、书架、分类、库存流水管理。
- `borrow`：借阅申请、借阅中、归还、逾期检测。
- `trade`：购书订单、优惠券核销、余额模拟支付、订单超时取消。
- `cart`：购物车。
- `coupon`：优惠券模板、用户优惠券。
- `seckill`：秒杀活动、抢券、异步发放优惠券。

关键入口：

- `GET /api/books`：图书列表。
- `POST /api/borrows`：创建借阅单。
- `POST /api/trade/orders`：创建购书订单。
- `POST /api/seckill/activities/{id}/grab`：秒杀抢券。

### 4. `com.zx.reservation` - 自习室预约

- 自习室资源、座位、时段管理。
- 预约单支持座位级预约，使用乐观锁控制并发。
- 到馆签到与连续签到奖励联动。

### 5. `com.zx.marketing.checkin` - 签到营销

- 基于预约单到馆扫码签到。
- 维护连续签到天数，满 7 天自动发放优惠券。

---

## 环境依赖

在启动项目前，请确保本地已安装并运行以下服务：

- JDK 21+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+
- Milvus 2.6+（仅在启用 RAG 时需要）
- Maven 3.9+

推荐前端联调地址：

- 默认后端端口：`http://localhost:8081`
- 默认前端成功回调：`http://localhost:5173/oauth/qq/success`

---

## 快速开始

### 1. 克隆并导入

```bash
git clone <repository-url>
cd Factory_Test_Demo
```

使用 IntelliJ IDEA 或 VS Code 打开项目，等待 Maven 依赖下载完成。

### 2. 创建本地配置

复制示例文件并填入真实账号密码：

```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

编辑 `src/main/resources/application-local.yaml`，填入：

- 邮箱 SMTP 账号与授权码（用于发送验证码）。
- MySQL 连接地址与密码。
- Redis 密码（如无密码留空）。
- QQ OAuth 的 `app-id` 与 `app-key`（仅使用 QQ 登录时需要）。

### 3. 初始化数据库

在 MySQL 中创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS test_demo
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

然后执行 `src/main/resources/db/schema.sql` 初始化表结构与示例数据。

### 4. 启动应用

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

或使用 IDEA 直接运行 `FactoryTestDemoApplication`，并设置 Active profiles 为 `local`。

### 5. 验证

应用启动后访问：

```bash
curl http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"loginType":"email_code","email":"admin@example.com","code":"123456"}'
```

> 默认示例账号 `admin` 的密码为 `123456`（仅用于本地开发）。

---

## 配置文件说明

### `application.yaml`

全局默认配置，包含：

- 数据源、Redis、RabbitMQ、邮箱连接。
- AI 模型参数（DeepSeek 对话模型、通义千问 Embedding 模型）。
- Milvus 向量库配置。
- 业务参数：会话 TTL、限流、借阅逾期、订单超时等。
- JWT 与 OAuth 参数。

### `application-local.yaml`（本地私有配置）

用于覆盖敏感信息，已加入 `.gitignore`，不会提交到版本库。请根据 `application-local.yaml.example` 创建。

### `application-param.yaml`

可选的外部配置文件，通过 `spring.config.import` 引入，可用于 CI/CD 或服务器环境注入密钥。

---

## 数据库初始化

所有表结构定义位于 `src/main/resources/db/schema.sql`。

此外，项目提供了若干增量脚本，用于特定功能升级：

- `migration-seat.sql`：座位粒度预约升级。
- `migration-borrow.sql` / `migration-borrow-overdue.sql`：借阅相关升级。
- `migration-book-shelf.sql` / `migration-book-stock-log.sql`：书架与库存流水升级。
- `migration-checkin.sql`：签到与连续签到。
- `migration-seckill.sql`：秒杀活动。

> 生产环境建议引入 Flyway 或 Liquibase 做版本化管理，避免手工执行 SQL。

---

## 主要 API

### 认证接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/code/send` | POST | 发送验证码 |
| `/api/auth/login` | POST | 登录 |
| `/api/auth/refresh` | POST | 刷新 Token |
| `/api/auth/logout` | POST | 退出登录 |
| `/api/auth/oauth/qq/state` | GET | 获取 QQ 登录 state |
| `/api/auth/oauth/qq/callback` | GET | QQ 登录回调 |

### AI 接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/ai/chat` | POST | 对话（匿名或登录） |
| `/api/ai/admin/reindex` | POST | 重建书目向量索引（ADMIN） |
| `/api/ai/admin/reindex-faq` | POST | 重建 FAQ 向量索引（ADMIN） |

### 书城管理接口（ADMIN）

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/admin/books` | GET / POST | 图书列表 / 创建图书 |
| `/api/admin/books/{id}` | GET / PUT / DELETE | 图书详情 / 更新 / 下架 |
| `/api/admin/book-categories` | GET / POST / PUT / DELETE | 分类管理 |
| `/api/admin/bookshelves` | GET / POST / PUT / DELETE | 书架管理 |

### 业务接口（需登录，具体权限视接口而定）

| 接口 | 说明 |
| --- | --- |
| `/api/books` | 图书列表与检索 |
| `/api/borrows` | 借阅单创建与查询 |
| `/api/trade/orders` | 购书订单 |
| `/api/cart/items` | 购物车 |
| `/api/seckill/activities` | 秒杀活动 |
| `/api/reservation/resources` | 自习室资源 |
| `/api/reservation/orders` | 预约订单 |
| `/api/checkin` | 签到 |

---

## AI 客服模块

AI 客服通过 System Prompt 强制约束大模型：涉及事实的查询必须先调用 Tool，禁止编造数据。已接入的工具有：

| Tool | 用途 |
| --- | --- |
| `searchBooks` | 按书名/作者/关键词检索图书 |
| `getBookDetail` | 查询图书详情与库存 |
| `searchFaq` | 查询业务规则与流程 |
| `getMyBorrowOrders` | 查询当前用户个人借阅 |
| `recommendBooks` | 图书推荐（热度 / 分类 / 个性化） |
| `getUserReadingProfile` | 获取用户阅读画像 |

会话键格式：

- 登录用户：`user:{userId}:{sessionId}`
- 匿名用户：`anon:{sessionId}`

会话隔离且存储于 Redis，默认 TTL 30 分钟，可在 `application.yaml` 中调整 `ai.session.ttl-minutes`。

---

## 测试

项目测试代码位于 `src/test/java`。当前测试覆盖较少，建议按 **[测试板块分步实现指南](docs/测试板块分步实现指南.md)**（T0～T6）逐步补齐，例如：

- AI 工具调用与提示词效果测试（CI 内 Mock，不调真模型）
- 认证登录、Token 刷新、OAuth 流程测试
- 书城借阅、订单、秒杀并发测试
- 预约座位乐观锁冲突测试
- 使用 Testcontainers 进行 MySQL / Redis / RabbitMQ 集成测试

运行测试：

```bash
mvn test
```

仅跑单元测试（排除 `*IT`）示例：

```bash
mvn -Dtest='!*IT' test
```

---

## 部署建议

### 开发环境

- 使用 `local` profile + `application-local.yaml`。
- 启用 HikariCP 默认连接池与 Redis 缓存。
- AI 模型使用 DeepSeek / 通义千问在线 API。

### 生产环境

- 必须外部化所有密钥（JWT Secret、数据库密码、邮箱授权码、API Key），禁止保留 `application.yaml` 中的默认值。
- 关闭 `spring.sql.init.mode` 或改为 `never`，避免启动时重复初始化数据。
- 引入 `spring-boot-starter-actuator` 暴露健康检查端点。
- 配置日志聚合（ELK / Loki）与监控告警（Prometheus + Grafana）。
- 对 Redis、RabbitMQ、Milvus 使用集群或云服务实例。
- 使用 Nginx / Gateway 做反向代理、HTTPS 终止与限流。

前端独立仓库的打包、Nginx 托管与回调 URL 对齐，见 **[前端部署指南](docs/前端部署指南.md)**。

---

## 补充文档

更多说明见 [`docs/`](docs/README.md)：

| 文档 | 说明 |
| --- | --- |
| [项目评价](docs/项目评价.md) | 完成度评价与生产差距 |
| [前端部署指南](docs/前端部署指南.md) | 前端打包与 Nginx 部署 |
| [后续学习路线](docs/后续学习路线.md) | 项目结束后的学习建议 |
| [加深方向学习路线](docs/learning/README.md) | 高并发 / 缓存 / MQ / AI 工程分册 |
| [测试板块分步实现指南](docs/测试板块分步实现指南.md) | T0～T6 测试补齐路线 |
| [IMPROVEMENTS.md](IMPROVEMENTS.md) | 可完善项清单（P0 / P1 / P2） |

---

## 安全提示

1. **JWT Secret**：生产环境必须替换 `auth.jwt.secret`，建议使用 256 位随机字符串并通过环境变量注入。
2. **数据库密码**：不要提交明文密码到版本库，使用 `application-local.yaml` 或环境变量。
3. **验证码**：开发环境示例验证码可能固定或打印在日志中，生产环境必须接入真实短信/邮件通道。
4. **QQ OAuth**：回调地址需与 QQ 互联平台配置一致，避免被伪造回调攻击。
5. **AI API Key**：通过环境变量 `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY` 注入，禁止写入配置文件。

---

## 许可证

本项目为学习演示用途，未指定特定开源许可证。如需商用，请自行替换示例数据、密钥与第三方服务配置。

---

**维护提示**：本文档随项目代码一起维护，新增模块或接口时请及时更新本 README。
