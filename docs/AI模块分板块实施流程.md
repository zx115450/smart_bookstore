# 智慧书城 AI 模块 — 分板块实施流程


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联**：`com.zx.ai` — 分板块落地顺序  
> **索引**：[学习文档中心](./README.md)

> 项目：`smart_bookstore`  
> 模块：`com.zx.ai`（AI 客服 + 智能推荐）  
> 前置：P0～P5 书城业务已通（图书/借阅/预约/认证）；[AI客服技术方案](./AI客服技术方案.md) · [BFF架构实战指南](./BFF架构实战指南.md)  
> 建议总周期：**2～3 周**（课余）；MVP 可 **1 周**（仅查书 + 简单对话）  
> 最后更新：2026-07-15

---

## 〇、当前落地进度（对照代码）

| 板块 | 状态 | 说明 |
|------|------|------|
| **A** 工程基座 | ✅ 已完成 | Spring AI BOM、`spring-ai-starter-model-openai`、DeepSeek Chat（OpenAI 兼容） |
| **B** Chat API + 会话 | ✅ 主体完成 | `POST /api/ai/chat`、ChatMemory + Advisor、Redis 上下文 |
| **B** 限流 / 可选 JWT | ✅ 完成 | 可选 JWT 已通（`OPTIONAL_AUTH_PATHS`）；限流 `AiRateLimiter` 待做 |
| **C** 查书 Tool | ✅ 完成 | `BookSearchTool` / `BookDetailTool` + `ChatResponse.cards`（type=book） |
| **D** FAQ Tool | ✅ 完成 | `FaqTool.searchFaq` 关键词打分 + 8 条业务规则；Prompt 路由规则类问题 |
| **E** 个人借阅 Tool | ✅ 完成 | `BorrowTool.getMyBorrowOrders` + `AiUserContext`；依赖 B.4 可选 JWT |
| **F** 规则推荐引擎 | ✅ 完成 | `HotBookRecaller` + `CategoryBookRecaller` + `BookRecommendService` + `BookRecommendTool`；cards 支持 type=recommend |
| **G** RAG + Milvus | ✅ 代码完成（默认关闭） | `ai.rag.enabled` 开关；`BookEmbeddingIndexer` / `SemanticBookRecaller` / `AiVectorConfig`；G.4 FAQ 向量分区（`FaqEmbeddingIndexer` / `SemanticFaqRecaller`）；启用需本地 Milvus |
| **H～J** | ⬜ 未开始 | 前端流式 / 日志收尾（H 个性化推荐已完成） |

**已落地包结构（当前）**：

```text
com.zx.ai
├── config/     AiProperties, AiChatConfig, AiVectorConfig
├── controller/ AiChatController, AiAdminController
├── dto/        ChatRequest, ChatResponse, ChatCard
├── memory/     RedisStringChatMemoryRepository
├── rag/        BookDocumentBuilder, BookEmbeddingIndexer, BookIndexPrewarmer
├── faq/        FaqEntry, FaqKnowledgeBase, FaqDocumentBuilder, FaqEmbeddingIndexer, SemanticFaqRecaller
├── service/    AiChatService
├── support/    ChatCardCollector, AiUserContext
├── recommend/  HotBookStat, HotBookRecaller, CategoryBookRecaller, RecommendItem, BookRecommendService, SemanticBookRecaller, UserReadingProfile, UserReadingProfileService, PersonalizedRecaller
├── tool/       BookSearchTool, BookDetailTool, BookToolViews, FaqTool, BorrowTool, BookRecommendTool, UserReadingProfileTool
└── exception/  AiException, AiExceptionHandler
```

**已落地接口**：

```text
POST /api/ai/chat
Body: { "sessionId": "可选", "message": "你好" }
Response: { "code":0, "data": { "sessionId", "reply", "cards": [{ "type":"book", "bookId", "title", ... }] } }
```

Redis Key：`ai:chat:memory:anon:{sessionId}`（由 `MessageChatMemoryAdvisor` 自动读写）。

---

## 一、实施前检查（Day 0）

### 1.1 依赖的已有能力

| 能力 | 包/表 | AI 模块用途 |
|------|-------|-------------|
| JWT 鉴权 | `com.zx.auth` | 用户身份、个性化推荐、借阅查询 |
| 图书目录 + 架位 | `bookstore.catalog` | Tool 查书、`shelfLocation` |
| 借阅 | `bookstore.borrow` | Tool 查个人借阅、推荐画像 |
| 购书订单 | `bookstore.trade` | 推荐画像、热门统计 |
| Redis | 已有 | **会话记忆（ChatMemory）**、限流（与向量库分工，**不存向量**） |
| **Milvus** | **G 阶段部署** | **企业级向量库**：图书/FAQ 语义检索 |
| MySQL | 已有 | 书目权威数据源 |

### 1.2 环境准备

```text
□ JDK 21（与主工程一致）
□ MySQL + Redis 已运行
□ DeepSeek API Key（Chat）；通义 DashScope Key（Embedding，G 阶段才必须）
□ 密钥放环境变量或 gitignore 的 application-param.yaml，勿提交 Git
□ Milvus 2.x（仅 G 阶段；A～F 通过 exclude 自动配置避免强依赖）
□ Docker Desktop（运行 Milvus 时）
□ Apifox / Postman
□ 可选：Vue3 聊天组件（与后端并行）
```

### 1.3 实施原则

1. **每板块结束必须可演示**（Postman 或前端聊天窗能跑通）  
2. **全 Java 栈**：Spring AI + Tool Calling + Milvus，不引入 Python 内网服务  
3. **查书/架位/库存走 Tool + MySQL**，禁止 LLM 编造  
4. **向量库是可选项**，规则推荐可先上线；**A～F 阶段 exclude Milvus 自动配置**  
5. **每板块更新** `docs/接口文档.md` AI 章节  
6. **不跨板块堆功能**（例如 Tool 未通不开 RAG）  
7. **模型协议**：Chat / Embedding 均走 **OpenAI 兼容协议**（`spring.ai.model.*=openai`），用 `base-url` 指向 DeepSeek / DashScope，少引专用 SDK  

### 1.4 板块总览

| 板块 | 名称 | 周期 | 里程碑 |
|------|------|------|--------|
| **A** | 工程基座 + Spring AI 接入 | 2 天 | 能调通大模型单轮回复 |
| **B** | 会话 + 限流 + Chat API | 2 天 | `/api/ai/chat` 多轮对话 |
| **C** | 查书 Tool（藏书 + 架位） | 2 天 | 「有 XX 书吗」「在几楼」 |
| **D** | FAQ Tool（业务规则） | 1 天 | 借书/签到/预约规则问答 |
| **E** | 个人借阅 Tool | 1 天 | 「我借的书在哪取」 |
| **F** | 规则推荐引擎 | 2 天 | 「推荐技术书」按分类/热门 |
| **G** | RAG + **Milvus** 向量检索 | 2～3 天 | 「想学 Redis」语义推荐 |
| **H** | 个性化推荐 | 2 天 | 「根据我借过的推荐」 |
| **I** | 前端 + SSE 流式 | 2 天 | 聊天 UI + 打字效果 |
| **J** | 日志 + 收尾 | 1 天 | 埋点、文档、答辩链路 |

**MVP 范围（约 1 周）**：**A + B + C + D**  
**完整版（答辩推荐）**：**A～J**

### 1.5 板块依赖关系

```text
A（基座）
 └─→ B（会话 API）
      └─→ C（查书 Tool）
           ├─→ D（FAQ）
           ├─→ E（借阅 Tool）
           └─→ F（规则推荐）
                ├─→ G（RAG 向量）  可选
                └─→ H（个性化）    依赖 E + F
                     └─→ I（前端 SSE）
                          └─→ J（收尾）
```

---

## 二、板块 A：工程基座 + Spring AI 接入（2 天）✅

**目标**：`com.zx.ai` 包就绪，能调用大模型返回一句中文。

### Step A.1 依赖与配置（0.5 天）✅

| 步骤 | 任务 | 产出 |
|------|------|------|
| A.1.1 | `pom.xml` 增加 Spring AI BOM + `spring-ai-starter-model-openai`（可选预加 milvus starter） | 编译通过 |
| A.1.2 | 新建包 `com.zx.ai` | 目录结构 |
| A.1.3 | `AiProperties` + `application.yaml` | 见下方 |
| A.1.4 | 密钥：`DEEPSEEK_API_KEY` / `AI_API_KEY`；可选 `application-param.yaml`（gitignore） | 本地可启动 |

**业务侧配置（`ai.*`）**：

```yaml
ai:
  enabled: true
  chat-model: deepseek-chat
  embedding-model: text-embedding-v3
  embedding-dimension: 1024
  temperature: 0.3
  max-history-turns: 6
  rate-limit:
    per-user-hourly: 30
  session:
    ttl-minutes: 30
```

**Spring AI 配置（OpenAI 兼容，Chat=DeepSeek）**：

```yaml
spring:
  config:
    import: optional:classpath:application-param.yaml
  autoconfigure:
    # A～F：未部署 Milvus 时必须 exclude，否则启动连不上向量库
    exclude:
      - org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration
  ai:
    model:
      chat: openai
      embedding: openai
    openai:
      chat:
        api-key: ${DEEPSEEK_API_KEY:${AI_API_KEY:}}
        base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
        options:
          model: ${ai.chat-model}
          temperature: ${ai.temperature}
      embedding:   # G 阶段才真正调用；可先配好
        api-key: ${DASHSCOPE_API_KEY:${AI_EMBEDDING_API_KEY:}}
        base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}
        options:
          model: ${ai.embedding-model}
          dimensions: ${ai.embedding-dimension}
```

> `chat: openai` **不是**调用 OpenAI 官方模型，而是使用 OpenAI 协议客户端；真正服务商由 `base-url` + `model` 决定。

### Step A.2 异常与错误码（0.5 天）✅

| 步骤 | 任务 | 产出 |
|------|------|------|
| A.2.1 | `AiException` + 错误码 **5xxx** | 与书城 4xxx 分离 |
| A.2.2 | `AiExceptionHandler`（`@RestControllerAdvice` 限定 `com.zx.ai.controller`） | 返回 `ApiResponse` |

| code | 含义 |
|------|------|
| 5001 | AI 服务不可用 / 上游超时 |
| 5002 | 限流 |
| 5003 | 会话不存在或已过期 |
| 5004 | 参数错误 |

### Step A.3 ChatClient Bean（0.5～1 天）✅

| 步骤 | 任务 | 文件 |
|------|------|------|
| A.3.1 | `AiChatConfig` 注册 `ChatClient`（`ai.enabled` 条件装配） | `config/AiChatConfig.java` |
| A.3.2 | System Prompt 初版（人设 + 禁止编造馆藏） | 同文件 |
| A.3.3 | （可选）日志观察上游失败原因 | 便于排错 |

> 早期可用 Smoke / 临时 Runner；本项目已直接进入 B 的 HTTP 接口验证。

### A 阶段自测

```text
☑ mvn compile 通过
☑ API Key 有效，能返回中文（经 /api/ai/chat）
☑ ai.enabled=false 时不加载 ChatClient
☑ 错误码 5xxx 与现有模块不冲突
☑ 未起 Milvus 时应用仍能启动（exclude 生效）
```

**交付物**：Spring AI 接入成功，可独立调模型。

---

## 三、板块 B：会话 + 限流 + Chat API（2 天）✅ / ⬜

**目标**：对外提供 `/api/ai/chat`，支持多轮上下文（Redis）。

### 设计选型：ChatMemory + Advisor（推荐，已采用）

| 方案 | 说明 | 本项目 |
|------|------|--------|
| 手写 Redis 历史 + `.messages(prior)` | 直观，但与 Spring AI 脱节 | ❌ 已废弃 |
| **ChatMemory + MessageChatMemoryAdvisor** | 官方链路；按 `conversationId` 自动读写 | ✅ 已采用 |
| 官方 Redis Memory Starter | 依赖 **Redis Stack**（RedisJSON/搜索） | ❌ 不适用普通 Redis |

**落地组合**：

1. `RedisStringChatMemoryRepository` 实现 `ChatMemoryRepository`（`StringRedisTemplate`）  
2. `MessageWindowChatMemory`：`maxMessages = max-history-turns * 2`  
3. `MessageChatMemoryAdvisor` 挂到 `ChatClient.defaultAdvisors`  
4. 调用时传入 `ChatMemory.CONVERSATION_ID`（当前为 `anon:{sessionId}`）

```java
aiChatClient.prompt()
    .user(message)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "anon:" + sessionId))
    .call()
    .content();
```

Redis：`ai:chat:memory:{conversationId}`，TTL = `ai.session.ttl-minutes`（每次写入续期）。

### Step B.1 会话存储（0.5 天）✅

| 步骤 | 任务 | 说明 |
|------|------|------|
| B.1.1 | `RedisStringChatMemoryRepository` | 实现 `findByConversationId` / `saveAll` / `delete` |
| B.1.2 | 消息序列化 | `{ type: USER\|ASSISTANT\|SYSTEM, content }` |
| B.1.3 | `MessageWindowChatMemory` Bean | 滑动窗口，与 `max-history-turns` 对齐 |
| B.1.4 | TTL | `ai.session.ttl-minutes`，默认 30 |

匿名用户：`conversationId = anon:{sessionId}`；登录用户隔离与可选 JWT 见 B.4。

### Step B.2 限流（0.5 天）⬜

| 步骤 | 任务 | 说明 |
|------|------|------|
| B.2.1 | `AiRateLimiter` | Redis 计数，每用户 30 次/小时 |
| B.2.2 | 未登录按 IP 限流 | Key 如 `ai:rate:ip:{ip}` |
| B.2.3 | 超限返回 5002 | 友好提示 |

### Step B.3 Chat API（1 天）✅

| 步骤 | 任务 | 文件 |
|------|------|------|
| B.3.1 | DTO | `ChatRequest`、`ChatResponse` |
| B.3.2 | `AiChatService.chat()` | 校验 → 生成/复用 sessionId → Advisor 调模型 |
| B.3.3 | `AiChatController` | `POST /api/ai/chat` |
| B.3.4 | Security + JWT 白名单 | `/api/ai/chat` 可匿名（`SecurityConfig` + `JwtAuthenticationFilter.PUBLIC_PATHS`） |
| B.3.5 | System Prompt | 人设 + 可答范围（暂不含 Tool） |

**接口**：

```text
POST /api/ai/chat
Body: { "sessionId": "uuid?", "message": "你好" }
Response: { "code":0, "data": { "sessionId", "reply" } }
```

**自测 curl**：

```bash
# 第一轮
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"你好，我叫小明\"}"

# 第二轮（带上返回的 sessionId）
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"<上一轮sessionId>\",\"message\":\"我叫什么名字？\"}"
```

### Step B.4 可选 JWT（增强）✅

| 步骤 | 任务 | 说明 |
|------|------|------|
| B.4.1 | `/api/ai/chat` **可选鉴权** | ✅ 无 Token 放行；有 Token 则解析 `AuthPrincipal` |
| B.4.2 | conversationId | ✅ 登录：`user:{userId}:{sessionId}`；匿名：`anon:{sessionId}` |
| B.4.3 | 改造 JWT Filter | ✅ 从 `PUBLIC_PATHS` 移除 `/api/ai/chat`，新增 `OPTIONAL_AUTH_PATHS`，无 Bearer 时匿名放行 |

> 限流 `AiRateLimiter` 仍未做（B.2），不影响 E。Token 过期/失效仍返回 401，触发前端 refresh。

### B 阶段自测

```text
☑ 连续多轮对话，第二轮能记住上下文
☑ 换 sessionId 上下文隔离
☑ Redis 里能看到 ai:chat:memory:* key
⬜ 超限返回 5002
☑ 有 Token 时写入 user: 前缀会话；无 Token 走 anon:
⬜ 更新 docs/接口文档.md AI 章节（B 部分）
```

**交付物**：可用的多轮聊天 API（纯对话，尚未查书）。

---

## 四、板块 C：查书 Tool（藏书 + 架位）（2 天）✅

**目标**：AI 能准确回答「有没有 XX 书」「在几楼第几层」。

### Step C.1 Tool 基础设施（0.5 天）✅

| 步骤 | 任务 | 说明 |
|------|------|------|
| C.1.1 | Spring AI `@Tool` 注册 | `ChatClient.defaultTools(...)` |
| C.1.2 | 薄适配层 `BookSearchTool` / `BookDetailTool` | 内部调 `BookCatalogService`，不改业务 Service |
| C.1.3 | 更新 System Prompt | **必须先 Tool 再回答**，禁止编造书目/架位 |

### Step C.2 实现 Tool（1 天）✅

| Tool | 实现类 | 调用 |
|------|--------|------|
| `searchBooks` | `BookSearchTool` | `BookCatalogService.listBooks(null, keyword, 1, limit)` |
| `getBookDetail` | `BookDetailTool` | `BookCatalogService.getBookDetail(id)` |

Tool 返回 JSON 须含：`id, title, author, borrowStock, saleStock, shelfLocation, status`（经 `BookToolViews` 裁剪）。

### Step C.3 响应卡片（0.5 天）✅

| 步骤 | 任务 | 说明 |
|------|------|------|
| C.3.1 | `ChatResponse.cards` | `type=book`（`ChatCard`） |
| C.3.2 | Tool 写入 + `AiChatService` drain | `ChatCardCollector` 收集 Tool 书目，供前端渲染 |

### C 阶段自测

```text
□ 「有没有 Redis 设计与实现」→ 调用 searchBooks，回复含架位
□ 「Java 核心技术在几楼」→ 含 shelfLocation，如 2楼 A-01 第3层
□ 不存在的书 → 明确说没有，不编造
□ 下架书（status=0）用户端查不到
□ Postman 保存 3 条用例
```

**交付物**：查书 + 架位问答准确，可答辩演示。

---

## 五、板块 D：FAQ Tool（业务规则）（1 天）✅

**目标**：回答借阅流程、签到送券、预约规则等（不查 MySQL 业务表）。

### Step D.1 FAQ 数据源（0.5 天）✅

| 方案 | 适用 | 做法 |
|------|------|------|
| **MVP** ✅ | 快速 | `FaqTool` 内静态知识库（`List<FaqEntry>`），8 条业务规则 |
| **增强** ⬜ | 可维护 | `docs/` 下 FAQ markdown 切片 + 小型 RAG（G 阶段复用向量库） |

已覆盖 FAQ 主题：

- 怎么借书 / 怎么还书 / 待取书在哪看架位
- 借阅到期与逾期（ZSET + Lua 自动标记 OVERDUE）
- 连续签到 7 天送券（CHECKIN_7 优惠券）
- 怎么预约自习室（resources → slots → seats → orders）
- 购书下单与自动取消（15 分钟延迟队列关单）
- 优惠券怎么用（签到/秒杀来源，FIXED/PERCENT 计算）

### Step D.2 Tool 与 Prompt（0.5 天）✅

| 步骤 | 任务 | 状态 |
|------|------|------|
| D.2.1 | `searchFaq(query)` 关键词打分匹配（含命中 +2 分词匹配 +1） | ✅ |
| D.2.2 | System Prompt 补充【规则类】必须走 searchFaq，禁止编造规则/接口/阈值 | ✅ |

### D 阶段自测

```text
□ 「连续签到 7 天有什么奖励」→ 提到 CHECKIN_7 优惠券
□ 「怎么预约自习室」→ 引导 reservation 流程
□ 超范围问题 → 引导人工或 /books
□ 「借的书到期了怎么办」→ 提到 OVERDUE 与归还入口
□ 「购书订单多久自动取消」→ 提到 15 分钟
```

---

## 六、板块 E：个人借阅 Tool（1 天）✅

**目标**：登录用户问「我借的书在哪取」「有什么待还」。

### Step E.1 鉴权要求

| 步骤 | 任务 | 状态 |
|------|------|------|
| E.1.1 | 完成 **B.4 可选 JWT** | ✅ 一并完成 |
| E.1.2 | `getMyBorrowOrders(status?)` 必须带 JWT | ✅ userId 取自 `AiUserContext`，不经 `@ToolParam` |
| E.1.3 | 未登录时 AI 回复「请先登录」 | ✅ Tool 返回未登录提示，Prompt 强制如实转达 |

### Step E.2 实现（0.5 天）✅

| Tool | 实现类 | 调用 | 返回字段 |
|------|--------|------|----------|
| `getMyBorrowOrders` | `BorrowTool` | `BorrowService.listMyOrders` | `orderNo, bookTitle, status, shelfLocation, borrowAt, dueAt, returnAt` |

**安全要点**：userId 来自 `AiUserContext`（由 `AiChatService` 从 JWT `AuthPrincipal` 注入到 ThreadLocal），LLM 无法通过 `@ToolParam` 伪造他人 id；`BorrowService.listMyOrders` 内部按 `principal.userId()` 过滤，物理隔离他人数据。

### E 阶段自测

```text
□ 登录用户有 APPLIED 订单 → 返回架位 + 取书提示
□ 未登录问「我的借阅」→ 提示登录
□ 不能查到其他 userId 的数据
□ 登录后会话 key 为 ai:chat:memory:user:{userId}:{sessionId}
```

---

## 七、板块 F：规则推荐引擎（2 天）✅

**目标**：「有什么技术书推荐」「文学区有什么」——不依赖向量库。

### Step F.1 推荐服务（1 天）✅

| 步骤 | 任务 | 文件 | 状态 |
|------|------|------|------|
| F.1.1 | `BookRecommendService` | 编排入口：召回→加热→回查 MySQL→重排序→生成理由 | ✅ |
| F.1.2 | `HotBookRecaller` | `borrow_order`(权重1) + `trade_order_item` PAID(权重2) 合并热度 TopN | ✅ |
| F.1.3 | `CategoryBookRecaller` | 按 `categoryId` + `borrowStock>0`；可选 keyword 书名召回兜底 | ✅ |
| F.1.4 | 重排序 | 可借(borrowStock>0) > 有架位(shelfLocation) > 热度 | ✅ |

热度 SQL（新增 mapper 方法）：

```sql
-- borrow_order: COUNT(*) GROUP BY book_id
-- trade_order_item JOIN trade_order(status='PAID'): SUM(quantity) GROUP BY book_id
```

### Step F.2 Recommend Tool（1 天）✅

| 步骤 | 任务 | 状态 |
|------|------|------|
| F.2.1 | `recommendBooks(intent, query?, categoryId?, limit?)` | ✅ `BookRecommendTool` |
| F.2.2 | 返回 `recommendReason` 字段 | ✅ 按来源拼接（分类/关键词/热度/可借） |
| F.2.3 | `ChatResponse.cards` 支持 `type=recommend` | ✅ `ChatCard.recommend` + `ChatCardCollector.offerRecommends` |
| F.2.4 | System Prompt：推荐必须走 Tool | ✅ 新增【推荐类】段 |

**intent 枚举**：`EXPLORE | LEARN | SIMILAR | RELAX`（`PERSONALIZED` 留 H 阶段，本阶段按通用规则处理）

### F 阶段自测

```text
□ 「推荐几本技术书」→ 返回 3～5 本，含理由和架位
□ 只推荐 status=1 的书（getBookDetail 已过滤下架）
□ 推荐结果可在 /api/books 查到对应 id
□ 模型未编造未在 Tool 结果中的书名
□ 「想学 Redis 推荐几本」→ 命中 Redis 相关可借书
□ 无数据时返回 found=false 引导换主题
```

---

## 八、板块 G：RAG + Milvus 向量检索（2～3 天）✅（代码完成，默认关闭）

**目标**：「想学 Redis / 入门 Spring」等模糊语义推荐；采用 **企业级向量数据库 Milvus**。

> **分工**：MySQL = 书目权威数据；Redis = 会话/限流/详情缓存；**Milvus = 向量专用存储**。
> **启用方式**：`ai.rag.enabled=true` + 本地启动 Milvus（G.0.1）+ 配置 `DASHSCOPE_API_KEY`。
> **默认关闭原因**：无 Milvus 时 `MilvusVectorStoreAutoConfiguration` 会被 `application.yaml` 的 exclude 屏蔽，避免启动失败；开启时 `AiVectorConfig` 显式 `@Import` 该自动装配。

### G.0 为什么选 Milvus

| 对比 | Redis Vector | **Milvus** |
|------|--------------|------------|
| 定位 | 内存缓存 + 轻量向量 | **专用向量数据库** |
| 持久化 | 依赖 RDB/AOF | **原生面向持久化与索引** |
| 规模 | 适合小数据量 | 百万～亿级向量 |
| Spring AI | redis starter | **`spring-ai-starter-vector-store-milvus`** |

### G.0.1 本地环境（Docker）

```bash
docker run -d --name milvus-standalone \
  -p 19530:19530 -p 9091:9091 \
  -v milvus_data:/var/lib/milvus \
  milvusdb/milvus:v2.4.4 \
  milvus run standalone
```

验证：`curl http://localhost:9091/healthz` 返回 OK。

### Step G.1 Embedding + Milvus 配置（0.5 天）✅

| 步骤 | 任务 | 状态 |
|------|------|------|
| G.1.1 | 确认已有 `spring-ai-starter-vector-store-milvus` | ✅ |
| G.1.2 | 通义 `text-embedding-v3`，dimension 1024 | ✅ `application.yaml` |
| G.1.3 | Collection `bookstore_book` | ✅ |
| G.1.4 | 移除/启用 Milvus autoconfigure | ✅ 默认 exclude；`AiVectorConfig` 显式 `@Import`（开关 `ai.rag.enabled`） |

新增配置（`application.yaml`）：

```yaml
ai:
  rag:
    enabled: false          # 默认关闭，开启需 Milvus 在线
    top-k: 8
    similarity-threshold: 0.6
    prewarm-on-startup: false
```

### Step G.2 离线索引（1 天）✅

| 步骤 | 任务 | 文件 | 状态 |
|------|------|------|------|
| G.2.1 | `BookDocumentBuilder` | 摘要文本 `title \| author \| 分类 \| description`（desc 截断 500） | ✅ |
| G.2.2 | `BookEmbeddingIndexer` | 全量 `reindexAll` / 增量 `reindexBook`（删旧 `book:{id}` 再写） | ✅ |
| G.2.3 | 启动预热或管理端「重建索引」 | `BookIndexPrewarmer`（prewarm 开关）+ `POST /api/ai/admin/reindex` | ✅ |
| G.2.4 | 改书时刷新 | 提供 `POST /api/ai/admin/reindex/{bookId}`；自动 hook 留后续 | ⬜ 可选 |

Document 文本示例：

```text
{title} | {author} | 分类:{categoryName} | {description}
```

**metadata**：`bookId, categoryId, type=book, status`。文档 id = `book:{bookId}`，便于删旧向量。

> 回查走 `BookCatalogService.getBookDetailAdmin`（不经布隆，含全部书，适合离线索引）。

### Step G.3 在线检索（1 天）✅

| 步骤 | 任务 | 文件 | 状态 |
|------|------|------|------|
| G.3.1 | `SemanticBookRecaller` | `vectorStore.similaritySearch` + filter `type == 'book' && status == 1` | ✅ |
| G.3.2 | 召回后回查 MySQL | `BookRecommendService` 加载 `getBookDetail` 补 shelfLocation/库存 | ✅ |
| G.3.3 | 接入 `BookRecommendService` | intent=LEARN/SIMILAR 优先语义，规则兜底；RAG 关闭自动降级 | ✅ |

```java
SearchRequest request = SearchRequest.builder()
    .query("想学 Redis 缓存")
    .topK(8)
    .similarityThreshold(0.6)
    .filterExpression("type == 'book' && status == 1")
    .build();
List<Document> hits = vectorStore.similaritySearch(request);
// metadata.bookId → BookCatalogService 回填架位库存
```

> `SemanticBookRecaller` 用 `@ConditionalOnBean(VectorStore.class)` 守卫；`BookRecommendService` 通过 `ObjectProvider` 注入，RAG 关闭时自动降级为纯规则召回。

### Step G.4 FAQ 向量（可选 0.5 天）✅

| 步骤 | 任务 | 状态 |
|------|------|------|
| G.4.1 | FAQ 向量分区（复用 `bookstore_book` collection，metadata `type=faq`） | ✅ |
| G.4.2 | `FaqTool` 升级为语义检索（关键词匹配兜底） | ✅ |

> **设计取舍**：Spring AI Milvus 自动装配只绑定单一 collection，新建第二个 `VectorStore` Bean 成本较高；
> 故 FAQ 与图书共用 `bookstore_book` collection，通过 metadata `type`（`book` / `faq`）分区过滤，
> 语义检索时 filter `type == 'faq'` 隔离。功能上等价于独立 `bookstore_faq` collection。
>
> **落地组件**（`com.zx.ai.faq`）：
> - `FaqEntry` / `FaqKnowledgeBase`：从 `FaqTool` 抽出的单一数据源，避免关键词匹配与向量索引文案不一致；
> - `FaqDocumentBuilder`：文本 `{topic} | {answer} | 关键词:...`，metadata `type=faq, topic`，doc id `faq:{index}`；
> - `FaqEmbeddingIndexer`：`reindexAll()` 先删旧 `faq:*` 向量再批量写入，仅 RAG 启用 + `VectorStore` 存在时装配；
> - `SemanticFaqRecaller`：`vectorStore.similaritySearch` + filter `type == 'faq'`，回填 `FaqEntry`；
> - `FaqTool`：注入 `ObjectProvider<SemanticFaqRecaller>`，RAG 启用时优先语义召回，无命中或 RAG 关闭时降级为关键词打分（D 板块能力保留兜底）。
>
> **管理端**：`POST /api/ai/admin/reindex-faq`（ADMIN）重建 FAQ 向量。
>
> D 板块关键词匹配仍可用；RAG 关闭时 `FaqTool` 自动降级，不影响 MVP。

### G 阶段自测

```text
□ ai.rag.enabled=true 且 Milvus 在线 → 启动成功，VectorStore Bean 存在
□ ai.rag.enabled=false（默认）→ 启动成功，无 Milvus 连接，推荐走规则
□ POST /api/ai/admin/reindex → 返回 indexed 条数
□ bookstore_book 向量条数 ≈ 上架图书数
□ 「想学缓存」语义召回 Redis 相关书（即使书名不含「缓存」）
□ 下架书被 filter 过滤；架位/库存与 MySQL 一致
□ 重启 Milvus 后数据仍在
```

**交付物**：企业级 Milvus 向量检索接入，语义推荐可演示（启用 `ai.rag.enabled=true` 后可用）。

---

## 九、板块 H：个性化推荐（2 天）✅

**目标**：「根据我借过的书推荐」「和刚还的书类似的」。

### Step H.1 用户画像（1 天）✅

| 步骤 | 任务 | 文件 | 状态 |
|------|------|------|------|
| H.1.1 | `getUserReadingProfile` Tool | `tool/UserReadingProfileTool.java` | ✅ |
| H.1.2 | 分类偏好、最近书目 | `recommend/UserReadingProfileService.java` + `UserReadingProfile.java` | ✅ |
| H.1.3 | `PersonalizedRecaller` | `recommend/PersonalizedRecaller.java`（同分类未借过 + 简化共现） | ✅ |

> **画像构建**：聚合 `borrow_order`（全部状态）+ `trade_order PAID` 的 items，得出
> `preferredCategories`（按命中次数降序 Top5）、`recentBookIds`（按 id 倒序 Top10）、
> `readBookIds`（已读，用于召回排除）、`activeBookIds`（在借未还，用于推荐排除）。
> 分类名通过 `BookCatalogService.listCategories()` 反查。
>
> **PersonalizedRecaller 召回两路**：
> 1. 同分类未借过：每个偏好分类下取可借图书，排除 `readBookIds`（权重 2）；
> 2. 简化共现：`BorrowOrderMapper.findCoBorrowedBooks` 找借过同样书的其他用户还借过什么，排除 `readBookIds`（权重 3 × 共现次数）。
>
> **安全**：`UserReadingProfileTool` 的 userId 来自 `AiUserContext`（JWT 注入 ThreadLocal），不经 `@ToolParam`，LLM 无法伪造他人 id。

### Step H.2 接入推荐（1 天）✅

| 步骤 | 任务 | 状态 |
|------|------|------|
| H.2.1 | `recommendBooks(intent=PERSONALIZED)` | ✅ `BookRecommendService` 新增 PERSONALIZED 分支 |
| H.2.2 | `recommendBooks(intent=SIMILAR, seedBookId=)` 结合 G | ✅ 种子书分类召回 + 种子书文本语义召回（RAG 启用时） |
| H.2.3 | 排除已借未还、已下架 | ✅ `activeBookIds` 排除 + `getBookDetail` 过滤 status=1 |

> **PERSONALIZED 流程**：未登录 → 返回空（Tool 层提示登录）；已登录 → 构建画像 →
> `PersonalizedRecaller.recall` → 不足时偏好分类兜底 → 回查 MySQL → 排除 `activeBookIds` 与下架书 → 重排序。
> **SIMILAR 流程**：`seedBookId` → 取种子书 `categoryId` 同类召回 + 种子书文本（title+分类+desc）走 `SemanticBookRecaller`（RAG 启用）→ 排除种子书自身 → 重排序。
> **推荐理由**：个性化加「根据你的借阅/购书记录推荐」；SIMILAR 加「与图书 #seedBookId 同类或相关」。

### H 阶段自测

```text
□ 用户借过 Java 书 → 推荐 Spring Boot 等（同分类未借过 + 共现）
□ 未登录要求个性化 → 提示登录
□ 推荐不包含用户当前在借未还的书（除非「续借」场景）
□ 「和这本书类似的」+ seedBookId → 返回同分类/语义相似书，不含种子书本身
□ 未登录调 getUserReadingProfile → 返回未登录提示
```

**交付物**：个性化推荐可演示（登录后「根据我借过的推荐」命中同分类/共现书，排除在借未还）。

---

## 十、板块 I：前端 + SSE 流式（2 天）⬜

**目标**：Vue3 聊天组件，可选打字机效果。

### Step I.1 后端 SSE（1 天）

| 步骤 | 任务 |
|------|------|
| I.1.1 | `POST /api/ai/chat/stream` |
| I.1.2 | `text/event-stream` 输出 delta |
| I.1.3 | 结束时推送 `done` + `cards` |

### Step I.2 前端（1 天）

| 步骤 | 任务 |
|------|------|
| I.2.1 | 全局悬浮聊天按钮 + 抽屉 |
| I.2.2 | 快捷 chips：「推荐技术书」「怎么借书」 |
| I.2.3 | 图书/推荐卡片 + 跳转 `/books/{id}` |
| I.2.4 | 更新 [前端生成提示词](./前端生成提示词.md) |

### I 阶段自测

```text
□ 流式输出逐字显示
□ cards 渲染推荐列表
□ 未登录可聊天；个性化提示登录
□ Token 过期走现有 refresh 逻辑
```

---

## 十一、板块 J：日志 + 收尾（1 天）⬜

### Step J.1 可选表 `ai_chat_log` / `ai_recommend_log`

| 字段 | 说明 |
|------|------|
| user_id, session_id | 谁问的 |
| message, reply | 问答内容（注意隐私脱敏） |
| tools_called | JSON，调了哪些 Tool |
| clicked_book_id | 推荐卡片点击 |

### Step J.2 文档与答辩

| 步骤 | 任务 |
|------|------|
| J.2.1 | 更新 `docs/接口文档.md` 完整 AI 章节 |
| J.2.2 | 更新 `docs/项目介绍.md` AI 能力 |
| J.2.3 | 准备答辩演示脚本 |

**答辩演示脚本（3 分钟）**：

```text
1. 「有没有 Spring Boot 实战」→ 查书 + 架位
2. 「想学 Redis 推荐几本」→ 推荐卡片 + 理由
3. 登录后「我借的书在哪取」→ 架位 + 订单状态
4. （可选）展示 Tool 调用日志 / 向量索引 / Redis 会话 Key
```

---

## 十二、常见问题与排错

| 现象 | 排查 |
|------|------|
| 启动报连不上 Milvus | 确认仍 exclude `MilvusVectorStoreAutoConfiguration`，或先起 Docker Milvus |
| Chat 返回 5001 | 检查 `DEEPSEEK_API_KEY`、网络、`base-url` 是否为 `https://api.deepseek.com` |
| 多轮无记忆 | 是否传回同一 `sessionId`；Redis 是否有 `ai:chat:memory:anon:*` |
| 模型答非所问/编造书 | Tool 未接或 Prompt 未强制「先 Tool」；temperature 保持 ≤ 0.3 |
| Embedding 维度报错 | `ai.embedding-dimension` 与 Milvus `embedding-dimension`、模型输出必须一致（本项目 1024） |
| 官方 Redis ChatMemory 起不来 | 需要 Redis Stack；本项目用自研 `RedisStringChatMemoryRepository` |

---

## 十三、包结构终态（目标）

```text
com.zx.ai
├── config/          AiProperties, AiChatConfig, AiToolConfig, AiVectorConfig
├── controller/      AiChatController
├── dto/             ChatRequest, ChatResponse, ChatCard, ...
├── memory/          RedisStringChatMemoryRepository
├── service/         AiChatService, BookRecommendService, AiRateLimiter
├── tool/            BookSearchTool, BookDetailTool, BookRecommendTool, FaqTool, ...
├── recommend/       HotBookRecaller, SemanticBookRecaller, PersonalizedRecaller, ...
├── rag/             BookDocumentBuilder, BookEmbeddingIndexer, BookVectorSearchService
├── exception/       AiException, AiExceptionHandler
└── support/         ChatCardCollector
```

---

## 十四、与现有错误码 / 安全约定

| 项 | 约定 |
|----|------|
| 错误码 | AI 模块 **5xxx** |
| 鉴权 | `/api/ai/chat` 可匿名；E/H 相关 Tool 需 JWT（依赖 B.4） |
| 幻觉防控 | 书目/架位/推荐 **必须 Tool**；temperature ≤ 0.3 |
| 数据权威 | MySQL > **Milvus 召回** > LLM 生成文本 |
| 存储分工 | Redis=会话记忆/限流/缓存；**Milvus=向量**；MySQL=业务事实 |
| 会话方案 | **ChatMemory + MessageChatMemoryAdvisor** + Redis Repository |

---

## 十五、周期参考（三种节奏）

| 节奏 | 板块 | 周期 |
|------|------|------|
| **极速 MVP** | A + B + C | 约 1 周 |
| **答辩推荐** | A～F + I | 约 2 周 |
| **完整版** | A～J | 约 3 周 |

---

## 十六、相关文档

| 文档 | 说明 |
|------|------|
| [AI客服技术方案](./AI客服技术方案.md) | 架构、Tool 设计、推荐策略 |
| [BFF架构实战指南](./BFF架构实战指南.md) | 单体 BFF、Spring AI 编排 |
| [前端生成提示词](./前端生成提示词.md) | 聊天 UI、API 对接 |
| [接口文档](./接口文档.md) | 每板块完成后更新 |
| [书城系统分阶段实施指南](./书城系统分阶段实施指南.md) | 主业务 P0～P5 |
| [项目介绍](./项目介绍.md) | 整体能力一览 |

---

## 十七、下一步（按优先级）

1. **板块 D**：FAQ Tool；或 **B.2 限流 / B.4 可选 JWT**
2. **板块 F**：推荐召回 + `type=recommend` 卡片
3. **更新** `docs/接口文档.md` AI 章节  
4. E → F 规则能力；**G Milvus** 在 F 跑通后再去掉 exclude、起 Docker  
5. 按本文顺序在 `com.zx.ai` 继续实现即可
