# 智慧书城 AI 模块学习文档

> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联包**：`com.zx.ai`  
> **技术**：Spring Boot 4.1 + Spring AI 2.0 + DeepSeek（对话）+ 通义千问 Embedding + 可选 Milvus（RAG）  
> **配套阅读**：[AI 客服技术方案](./AI客服技术方案.md) · [AI 模块分板块实施流程](./AI模块分板块实施流程.md) · [Spring AI 框架核心内容学习文档](./SpringAI框架核心内容学习文档.md) · [向量检索策略与存储单元](./向量检索策略与存储单元.md)  
> **索引**：[学习文档中心](./README.md)

本文面向「从零理解本项目 AI 客服」的学习路径：先建立心智模型，再对照代码走通一次对话，最后按板块深入。

---

## 1. 先建立心智模型

### 1.1 一句话架构

**Controller 管会话与鉴权 → ChatClient + System Prompt + Memory Advisor 编排 → Tool 查权威业务数据并收集卡片 → RAG 只增强「模糊语义召回」，可整体关掉并降级为规则 / SQL。**

### 1.2 职责边界（最重要）

| 角色 | 做什么 | 不做什么 |
| --- | --- | --- |
| 大模型（LLM） | 理解用户意图、决定调哪个 Tool、组织中文回复 | 不编造书名、库存、架位、借阅单、业务规则 |
| Tool | 访问 MySQL / 业务 Service，返回真实 JSON | 不负责「话术润色」 |
| RAG（Milvus） | 用向量相似度召回候选书 / FAQ | 不直接当库存真相源；命中后仍回查业务库 |
| Redis Memory | 多轮对话上下文 | 不存向量、不存业务主数据 |

记忆口诀：**事实走 Tool，话术走模型，语义召回可选增强。**

### 1.3 你要掌握的 5 个概念

1. **ChatClient**：Spring AI 对「发一轮对话」的封装，可挂 System Prompt、Advisor、Tools  
2. **Tool Calling（Function Calling）**：模型输出「要调哪个函数 + 参数」，框架执行后把结果回灌给模型，再生成最终回复  
3. **Advisor**：对话前后的拦截器；本项目用 `MessageChatMemoryAdvisor` 自动读写历史  
4. **RAG（Retrieval-Augmented Generation）**：先检索相关文档，再让模型基于检索结果回答；本项目向量只做召回，事实仍以业务库为准  
5. **Session / Conversation**：前端用 `sessionId` 续聊；后端拼 `conversationId` 隔离登录用户与匿名用户

---

## 2. 推荐学习顺序

按下面顺序读代码，比「从入口乱翻」效率更高。

| 阶段 | 目标 | 建议阅读 |
| --- | --- | --- |
| Day 1 | 跑通一次对话 | `AiChatController` → `AiChatService` → `AiChatConfig` |
| Day 2 | 搞懂 Tool | `tool/*`、`support/AiUserContext`、`support/ChatCardCollector` |
| Day 3 | 搞懂记忆 | `memory/RedisStringChatMemoryRepository`、`ai.session.*` |
| Day 4 | 搞懂 FAQ / 推荐 | `faq/*`、`recommend/*`、`BookRecommendTool` |
| Day 5 | 搞懂 RAG | `config/AiVectorConfig`、`rag/*`、`Semantic*Recaller`、Admin reindex |
| Day 6 | 对照框架理论 | [SpringAI框架核心内容学习文档](./SpringAI框架核心内容学习文档.md) |

环境最小集（不做 RAG 也可学 Tool 对话）：

- JDK 21、MySQL、Redis  
- 环境变量 `DEEPSEEK_API_KEY`  
- 若开启 RAG：再加 Milvus + `DASHSCOPE_API_KEY`

关闭 RAG 学习路径：在配置中设 `ai.rag.enabled=false`，查书 / FAQ 关键词 / 热门推荐仍可用。

---

## 3. 包结构地图

根说明见 `com.zx.ai.package-info`。

```text
com.zx.ai
├── config/      ChatClient、向量库、业务配置
├── controller/  HTTP：对话 + 管理端重建索引
├── service/     会话编排（校验、会话键、调模型、收卡片）
├── tool/        暴露给 LLM 的 @Tool 适配层
├── memory/      Redis 多轮对话记忆
├── faq/         静态规则库 + 可选 FAQ 向量
├── rag/         书目 Document 构建与向量写入
├── recommend/   多路召回 → 回查 → 精排
├── support/     ThreadLocal：用户上下文、前端卡片
├── dto/         ChatRequest / ChatResponse / ChatCard
└── exception/   AiException + Controller 级异常处理
```

| 子包 | 学习时抓住的「一件事」 |
| --- | --- |
| `config` | System Prompt 如何约束模型必须调 Tool |
| `service` | `conversationId` 怎么拼、ThreadLocal 为何要 finally 清理 |
| `tool` | 每个 `@Tool` 对应一类用户意图 |
| `memory` | Redis Key 与滑动窗口轮数 |
| `faq` / `rag` | 同一 collection 用 `type` 区分 book / faq |
| `recommend` | 意图 → 召回器 → 业务库回查 → 推荐理由 |
| `support` | 卡片如何从 Tool 传到 HTTP 响应 |

---

## 4. 一次完整对话怎么走

### 4.1 时序（文字版）

```text
前端 POST /api/ai/chat
  Body: { "sessionId": "可选", "message": "有没有《三体》" }
        ↓
AiChatController（permitAll，可选 JWT）
        ↓
AiChatService
  1. 校验 message（非空、≤ 2000 字）
  2. 生成或复用 sessionId
  3. 拼 conversationId：
       登录 → user:{userId}:{sessionId}
       匿名 → anon:{sessionId}
  4. AiUserContext.setUserId(...)；ChatCardCollector.clear()
  5. ChatClient.prompt().user(message)
       .advisors(CONVERSATION_ID = conversationId)
       .call().content()
        ↓
MessageChatMemoryAdvisor：从 Redis 读历史，写入本轮
ToolCallingAdvisor：模型选 Tool → 执行 → 回灌 → 直到产出最终文案
        ↓
Tool（如 searchBooks）查库，并把书卡片 offer 进 ChatCardCollector
        ↓
AiChatService.drain() 取出 cards，返回 ChatResponse
finally：清理 ThreadLocal
```

### 4.2 响应长什么样

```json
{
  "code": 0,
  "data": {
    "sessionId": "abc123",
    "reply": "馆内有《三体》，架位在……",
    "cards": [
      {
        "type": "book",
        "bookId": 1,
        "title": "三体",
        "shelfLocation": "..."
      }
    ]
  }
}
```

多轮续聊时，**必须把上一轮返回的 `sessionId` 原样回传**，否则会当成新会话。

### 4.3 关键代码入口

- 对话：`AiChatService.chat`
- 装配：`AiChatConfig.aiChatClient`（System Prompt + Memory + Tools）
- HTTP：`POST /api/ai/chat`

---

## 5. System Prompt：为什么模型「不敢乱编」

核心策略不在「模型很聪明」，而在 **提示词强制路由**：

| 用户意图 | 必须先调用的 Tool | 禁止行为 |
| --- | --- | --- |
| 查书 / 库存 / 架位 | `searchBooks` / `getBookDetail` | 编造书名、架位、库存 |
| 业务规则（借还、预约、优惠券等） | `searchFaq` | 编造规则细节或数值 |
| 我的借阅 | `getMyBorrowOrders` | 未登录却假装查到数据 |
| 推荐书 | `recommendBooks` | 编造推荐书名 |
| 个性化推荐 | 先 `getUserReadingProfile`，再 `recommendBooks(PERSONALIZED)` | 忽略未登录提示 |

完整文案见 `AiChatConfig.SYSTEM_PROMPT`。学习时建议：**一边改一句 Prompt，一边用 Apifox 测同一问题**，观察模型是否仍会乱调 Tool。

---

## 6. Tool 层精讲

Tool 是 LLM 与业务域之间的适配层：参数尽量简单，返回瘦 JSON，身份信息不让模型传。

### 6.1 工具一览

| Tool 名 | 类 | 典型用户话术 | 说明 |
| --- | --- | --- | --- |
| `searchBooks` | `BookSearchTool` | 「有没有三体」 | 关键词检索；写 `type=book` 卡片 |
| `getBookDetail` | `BookDetailTool` | 「这本书库存多少」 | 按 `bookId` 详情；写 book 卡 |
| `searchFaq` | `FaqTool` | 「怎么借书」 | 语义优先，关键词兜底 |
| `getMyBorrowOrders` | `BorrowTool` | 「我借了什么」 | `userId` 来自 `AiUserContext` |
| `recommendBooks` | `BookRecommendTool` | 「推荐几本小说」 | intent 见下表；写 recommend 卡 |
| `getUserReadingProfile` | `UserReadingProfileTool` | 「根据我借过的推荐」 | 只出画像，推荐仍走 `recommendBooks` |

### 6.2 推荐意图 `intent`

| 值 | 含义 |
| --- | --- |
| `EXPLORE` | 随便看看 / 有什么书 |
| `LEARN` | 想学某主题（常带 query） |
| `SIMILAR` | 找类似的（可传 `seedBookId`） |
| `RELAX` | 休闲读物 |
| `PERSONALIZED` | 基于个人借阅 / 购书画像 |

### 6.3 两个安全约定（面试常问）

1. **禁止模型传 `userId`**：个人类 Tool 从 `AiUserContext` 取当前登录用户，避免越权查别人订单  
2. **卡片去重**：`ChatCardCollector` 按 `bookId` 去重，同一轮多次查同一本书只出一张卡

### 6.4 FAQ 知识库主题（当前 8 条）

怎么借书 / 怎么还书 / 待取书与架位 / 借阅到期与逾期 / 连续签到奖励 / 怎么预约自习室 / 购书下单与自动取消 / 优惠券怎么用。

数据源：`FaqKnowledgeBase`（静态列表）。改规则优先改这里，再视需要重建 FAQ 向量索引。

---

## 7. 会话记忆（Redis）

| 项 | 说明 |
| --- | --- |
| 仓库实现 | `RedisStringChatMemoryRepository`（普通 Redis String，不依赖 Redis Stack） |
| Key | `ai:chat:memory:{conversationId}` |
| 窗口 | `maxHistoryTurns * 2`（默认 6 轮 → 最多约 12 条 message） |
| TTL | `ai.session.ttl-minutes`（默认 30） |
| 注意 | 实现里会跳过 TOOL 类型消息，只保留对人可读的对话痕迹 |

Advisor 用法（业务侧只需传 conversationId）：

```java
.aiChatClient.prompt()
    .user(message)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
    .call()
    .content();
```

---

## 8. 推荐引擎（规则 + 可选语义）

入口：`BookRecommendService.recommend(...)`，由 `BookRecommendTool` 调用。

### 8.1 多路召回

| 召回器 | 策略 |
| --- | --- |
| `HotBookRecaller` | 借阅热度 + 已支付销量 |
| `CategoryBookRecaller` | 分类 / 关键词上架可借书 |
| `SemanticBookRecaller` | Milvus 语义相似（仅 RAG 开启） |
| `PersonalizedRecaller` | 同分类未读 + 共现借阅 |

### 8.2 编排要点

1. `PERSONALIZED`：未登录直接空结果并提示；有历史则个性化召回，不足用偏好分类补  
2. `SIMILAR` + `seedBookId`：同分类 + 种子书文本语义召回  
3. `LEARN` / 带 query：语义优先，再分类 / 关键词，仍空则热门兜底  
4. **一律回查** `getBookDetail`（只保留上架），排序：**可借 > 有架位 > 热度**  
5. `buildReason` 生成中文 `recommendReason`，供模型组织回复与前端卡片展示

画像：`UserReadingProfileService` 聚合借阅 + 已支付购书。

---

## 9. RAG：向量检索怎么嵌进业务

### 9.1 何时用向量，何时用 SQL

| 能力 | RAG 开 | RAG 关 |
| --- | --- | --- |
| 精确查书 | 仍走 SQL Tool | 同左 |
| FAQ | 语义优先 → 关键词 | 仅关键词 |
| LEARN / SIMILAR 推荐 | 语义召回增强 | 热度 / 分类 / 关键词 |
| 个性化推荐 | 主要靠 SQL 共现 | 同左 |
| Admin 重建索引 | 真正写 Milvus | 返回 `enabled=false` |

### 9.2 索引怎么建

| 组件 | 作用 |
| --- | --- |
| `BookDocumentBuilder` | 书目 → Embedding 文本 + metadata（`type=book`） |
| `BookEmbeddingIndexer` | 全量 / 单本写入向量库 |
| `FaqDocumentBuilder` / `FaqEmbeddingIndexer` | FAQ 写入**同一 collection**，`type=faq` |
| `BookIndexPrewarmer` | `prewarm-on-startup=true` 时启动全量预热（默认关） |

集合名默认：`bookstore_book`。书目与 FAQ 靠 metadata 字段 `type` 过滤，避免互相污染。

### 9.3 配置注意点

- 默认 `application.yaml` **exclude** Milvus 自动装配，避免未装 Milvus 时启动失败  
- `ai.rag.enabled=true` 时由 `AiVectorConfig` **显式 Import** 自动装配  
- `initialize-schema: false`：集合与索引需事先建好（兼容性约束）  
- Embedding：通义 `text-embedding-v3`，维度 `1024`；Chat：DeepSeek

### 9.4 管理端接口（需 ADMIN）

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/api/ai/admin/reindex` | 重建全部书目向量 |
| POST | `/api/ai/admin/reindex/{bookId}` | 重建单本书 |
| POST | `/api/ai/admin/reindex-faq` | 重建 FAQ 向量 |

书目变更后若开启了 RAG，应记得重建索引，否则语义召回会过期。

---

## 10. 关键配置速查

业务侧（`ai.*`）：

```yaml
ai:
  enabled: true
  chat-model: deepseek-chat
  embedding-model: text-embedding-v3
  embedding-dimension: 1024
  temperature: 0.3
  max-history-turns: 6
  rate-limit:
    per-user-hourly: 30   # 配置已有，实现以代码为准（可能仍待补）
  session:
    ttl-minutes: 30
  rag:
    enabled: true
    top-k: 8
    similarity-threshold: 0.6
    prewarm-on-startup: false
```

密钥（勿写入仓库）：

- `DEEPSEEK_API_KEY`：对话  
- `DASHSCOPE_API_KEY`：Embedding（开 RAG 时需要）

框架侧还有 `spring.ai.openai.*`、`spring.ai.vectorstore.milvus.*`，细节见 `application.yaml` 与 README。

---

## 11. 动手实验清单

按顺序做，每做完一项在纸上写「调了哪个 Tool / 有没有卡片」：

1. 匿名问：「你好」→ 应无 Tool 或极少 Tool，纯寒暄  
2. 「有没有三体」→ 应出现 `searchBooks`，响应含 `cards`  
3. 「怎么借书」→ 应出现 `searchFaq`  
4. 不登录问「我借了什么」→ 应提示登录，不能编造订单  
5. 登录后问「根据我借过的推荐几本」→ `getUserReadingProfile` + `recommendBooks(PERSONALIZED)`  
6. 同一 `sessionId` 连续两轮，确认第二轮能引用上文  
7.（可选）开 RAG 后问模糊主题，对比关 RAG 时推荐质量差异  
8.（可选）Admin 调用 `/reindex`、`/reindex-faq`，看返回 `indexed` 数量

---

## 12. 常见误区

| 误区 | 正解 |
| --- | --- |
| 「有了大模型就不用查库」 | 本项目明确禁止编造；事实必须 Tool |
| 「RAG 可以替代库存接口」 | RAG 只召回候选；库存 / 架位仍回查 MySQL |
| 「关掉 Milvus 就不能对话」 | 可以；语义能力降级，SQL / 关键词仍可用 |
| 「sessionId 可有可无」 | 多轮必须回传，否则记忆断裂 |
| 「让模型传 userId 更方便」 | 有越权风险；用服务端 `AiUserContext` |
| 「改了书目立刻语义可搜」 | 需重建向量索引（或预热任务） |

---

## 13. 和现有文档怎么分工

| 文档 | 适合什么时候读 |
| --- | --- |
| **本文（学习文档）** | 入门、建立全貌、按 Day 计划读代码 |
| [AI客服技术方案](./AI客服技术方案.md) | 要写设计说明 / 方案评审 |
| [AI模块分板块实施流程](./AI模块分板块实施流程.md) | 对照 A～H 落地清单与实现顺序 |
| [AI客服全链路实现思路与方案学习文档](./AI客服全链路实现思路与方案学习文档.md) | 对外讲解全链路故事 |
| [SpringAI框架核心内容学习文档](./SpringAI框架核心内容学习文档.md) | 补 Spring AI 框架层概念 |
| [向量检索策略与存储单元](./向量检索策略与存储单元.md) | 深入 embedding、collection、过滤策略 |
| [README](../README.md) | 环境启动与接口总表 |

---

## 14. 自测题（学完能答出来）

1. 为什么 System Prompt 要写「必须先调用 xxx」，而不是只写「你是一个助手」？  
2. `user:{id}:{session}` 和 `anon:{session}` 分开有什么安全与产品意义？  
3. Tool 返回 JSON 后，最终中文回复是谁生成的？卡片又是谁收集的？  
4. FAQ 与书目共用一个 Milvus collection 时，如何避免检索串台？  
5. 推荐链路里，语义召回之后为什么还要 `getBookDetail`？  
6. 若 `ai.rag.enabled=false`，`LEARN` 意图大致会怎么降级？

能口头讲清以上 6 题，说明你已经抓住本模块的主干。

---

## 15. 延伸：下一步可以练什么

- 给某个 Tool 补集成测试（mock `ChatClient` 或只测 Service / Recaller）  
- 补齐 `ai.rate-limit` 的真实限流实现（配置字段已存在）  
- 为聊天接口加 SSE / 流式输出（前端体验）  
- 观察一次真实 Tool Calling 日志，画自己的时序图

学习原则：**先跑通，再改 Prompt，再动召回，最后才碰向量与运维。**
