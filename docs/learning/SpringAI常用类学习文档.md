# Spring AI 常用类：职责、工作原理与加载机制

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 类辞典 + 装配链路  
> **版本锚点**：Spring AI `2.0.0`（见 `pom.xml`）  
> **建议前置**：[AI 模块学习文档](../AI模块学习文档.md) · [Spring AI 框架核心内容](./SpringAI框架核心内容学习文档.md)

## 学习目标

1. 能按「编排 / 模型 / 消息 / 记忆 / 工具 / 向量」六层说出常用类各自干什么
2. 理解一次 `ChatClient.call()` 从 Advisor 到 `ChatModel` 再到 Tool 回灌的调用链
3. 说清这些 Bean 如何被 Spring Boot 自动配置加载，以及本仓库如何条件装配

## 与本仓库的对应关系

| 路径 | 对照点 |
| --- | --- |
| `com.zx.ai.config.AiChatConfig` | `ChatClient` / `ChatMemory` / Tools 装配 |
| `com.zx.ai.service.AiChatService` | `prompt()` + `CONVERSATION_ID` |
| `com.zx.ai.memory.RedisStringChatMemoryRepository` | 自实现 `ChatMemoryRepository` |
| `com.zx.ai.tool.*` | `@Tool` / `@ToolParam` |
| `com.zx.ai.rag.*` / `com.zx.ai.faq.*` | `Document` / `VectorStore` / `SearchRequest` |
| `com.zx.ai.config.AiVectorConfig` | 条件 `@Import` Milvus 自动配置 |

---

## 一、总览：常用类按层归类

```text
应用层     AiChatService / Controller
              │
编排层     ChatClient  ←── ChatClient.Builder（自动配置提供）
              │
横切层     Advisor 链（Memory / ToolCalling / 自定义）
              │
模型层     ChatModel / EmbeddingModel
              │
数据契约   Prompt · Message · ChatResponse · Document
              │
能力扩展   @Tool · ToolCallback · ChatMemory · VectorStore
```

| 层级 | 常用类型 | 一句话 |
| --- | --- | --- |
| 编排 | `ChatClient` | 业务侧对话门面 |
| 模型 | `ChatModel` / `EmbeddingModel` | 真正调 HTTP API 的实现 |
| 请求 | `Prompt` / `ChatOptions` | 消息列表 + 采样参数 |
| 消息 | `UserMessage` 等 | 多轮对话的原子单元 |
| 响应 | `ChatResponse` | 模型返回的结构化结果 |
| 横切 | `Advisor` / `CallAdvisor` | 记忆、工具循环、RAG 注入 |
| 记忆 | `ChatMemory` / `ChatMemoryRepository` | 窗口策略 + 持久化 |
| 工具 | `@Tool` / `ToolCallback` | 把 Java 方法暴露给模型 |
| 向量 | `Document` / `VectorStore` / `SearchRequest` | RAG 写入与相似度检索 |

日常写业务：**90% 时间碰 `ChatClient` + `@Tool` + `ChatMemory`**；换厂商或排障时再下钻 `ChatModel`。

---

## 二、编排层

### 2.1 `ChatClient`

**干什么**

Spring AI 推荐的日常入口。链式拼 System / User Prompt、挂 Tools、挂 Advisors，然后 `.call()` 或 `.stream()` 拿回复。

**工作原理**

1. `prompt()` 开启一次请求构建
2. 合并 `defaultSystem` / `defaultTools` / `defaultAdvisors` 与本轮覆盖项
3. 走 Advisor 链（前后置）
4. 最终委托底层 `ChatModel` 发请求
5. 若模型返回 tool_calls，由 `ToolCallingAdvisor` 执行工具并回灌，直到产出最终文本

本仓库用法（节选）：

```java
String reply = aiChatClient.prompt()
        .user(message)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
```

见 `AiChatService`。

**加载机制**

- Starter：`spring-ai-starter-model-openai` 自动配置提供 `ChatClient.Builder` Bean
- 业务在 `AiChatConfig` 里 `builder.defaultSystem(...).defaultAdvisors(...).defaultTools(...).build()` 得到名为 `aiChatClient` 的 `ChatClient`
- 受 `ai.enabled=true`（默认）控制；关掉则整条对话 Bean 不创建

---

### 2.2 `ChatClient.Builder`

**干什么**

不可变 `ChatClient` 的工厂。用来设「每次对话都默认带上」的行为，避免在 Service 里重复拼装。

**工作原理**

Builder 持有默认 System、Advisor 列表、Tool 列表、Options；`build()` 后每次 `prompt()` 都复制/合并这些默认值，本轮仍可用 `.system()` / `.tools()` / `.advisors()` 覆盖或追加。

**加载机制**

由模型 starter 的 AutoConfiguration 创建；你只注入 `ChatClient.Builder`，不必 `new`。

---

## 三、模型层

### 3.1 `ChatModel`

**干什么**

对话模型的最低层抽象：输入 `Prompt`，输出 `ChatResponse`。对接 OpenAI 兼容协议时，实现类负责拼 HTTP、解析 SSE / JSON。

**工作原理**

```text
Prompt(messages + options)
  → 序列化为厂商 API 请求体
  → HTTP 调用（如 DeepSeek / 通义兼容端点）
  → 反序列化为 ChatResponse（含 Generation / metadata）
```

业务代码通常**不直接**调 `ChatModel`，而是经 `ChatClient`。理解它是为了：换模型、看超时、查 token 用量、排「模型连不上」类故障。

**加载机制**

- 依赖 `spring-ai-starter-model-openai`（本仓库 Chat 与 Embedding 都走 OpenAI 兼容客户端）
- 配置项形如 `spring.ai.openai.api-key` / `base-url` / `chat.options.model`
- Boot 扫描 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，满足条件后注册 `ChatModel` Bean，再被 `ChatClient.Builder` 引用

Chat 与 Embedding **是两条线**：对话用 `ChatModel`，向量化用 `EmbeddingModel`，可配不同 `base-url` / `model`。

---

### 3.2 `EmbeddingModel`

**干什么**

把文本变成浮点向量，供写入 / 检索向量库。

**工作原理**

`embed(String)` 或批量 embed → 得到 `float[]` / `Embedding`；`VectorStore.add(Document)` 内部会调它再写入 Milvus 等。

本仓库：书目 / FAQ 索引时由 `VectorStore` 间接使用；检索时 `similaritySearch` 也会先把 query 向量化。

**加载机制**

与 Chat 同属 OpenAI 兼容 starter 的自动配置（`spring.ai.openai.embedding.*`）。RAG 关闭时，业务侧用 `@ConditionalOnBean(VectorStore.class)` 避免强依赖向量链路。

---

## 四、请求与响应契约

### 4.1 `Prompt`

**干什么**

一次模型调用的完整输入：消息列表 + 可选 `ChatOptions`（温度、maxTokens、模型名等）。

**工作原理**

`ChatClient` 在 Advisor 跑完前置逻辑后，把当前消息组装成 `Prompt`，交给 `ChatModel.call(prompt)`。你很少手写 `new Prompt(...)`，但调试 Advisor / Memory 时会在日志里看到它。

---

### 4.2 `ChatOptions`（及厂商 Options）

**干什么**

控制采样与模型选择：`temperature`、`maxTokens`、`model` 等。

**工作原理**

合并顺序一般是：全局配置 → `ChatClient` 默认 Options → 本轮 `.options(...)`。最终进入请求 JSON 的 `model` / `temperature` 字段。

**加载机制**

`application.yaml` 里 `spring.ai.openai.chat.options.*` 绑定到自动配置；也可在代码里按请求覆盖。

---

### 4.3 `ChatResponse`

**干什么**

模型一次调用的结构化结果：候选生成（`Generation`）、用量、finishReason、是否含 tool_calls 等。

**工作原理**

- `.call().content()`：取第一条生成的文本（本仓库常用）
- `.call().chatResponse()`：拿完整对象做观测或自定义解析
- Tool 循环中，中间轮次的 `ChatResponse` 可能只有 tool_calls、没有最终对用户文案

注意：自动 Tool 循环结束后，**最终** `ChatResponse` 通常只有模型口语化回复，**不含** Tool 原始 JSON。本仓库因此用 `ChatCardCollector` 在 Tool 执行时旁路收集图书卡片。

---

## 五、消息体系

### 5.1 `Message` 与 `MessageType`

**干什么**

多轮对话的原子消息。类型大致包括：

| 类型 | 典型类 | 含义 |
| --- | --- | --- |
| SYSTEM | `SystemMessage` | 角色与硬规则 |
| USER | `UserMessage` | 用户输入 |
| ASSISTANT | `AssistantMessage` | 模型输出（可含 tool_calls） |
| TOOL | Tool 相关消息 | 工具执行结果回灌 |

**工作原理**

Memory Advisor 读写的就是 `List<Message>`；落库时按 type 序列化。本仓库 `RedisStringChatMemoryRepository` **跳过 TOOL 类型**，避免工具中间态占会话与 token。

**加载机制**

非 Bean；由框架与业务在运行时 `new`。Repository 反序列化时按 type 字符串还原为具体 `Message` 实现。

---

### 5.2 `UserMessage` / `AssistantMessage` / `SystemMessage`

**干什么**

三种最常见角色消息。`SystemMessage` 常来自 `ChatClient.defaultSystem`；`UserMessage` 来自 `.user(...)`；`AssistantMessage` 来自模型或历史回放。

本仓库 System 文案写在 `AiChatConfig.SYSTEM_PROMPT`，强制「事实走 Tool、禁止编造」。

---

## 六、Advisor：横切编排

### 6.1 `Advisor` / `CallAdvisor`

**干什么**

对话前后的拦截器链（类似 Filter）。在真正调模型前后插入：读记忆、写记忆、Tool 循环、RAG 拼上下文、日志审计等。

**工作原理（简化）**

```text
请求进入
  → Advisor1.around / before
  → Advisor2...
  → ChatModel.call
  → Advisor2 after...
  → Advisor1 after
  → 返回给业务
```

Spring AI 2.0 把 **Tool Calling 循环**做成链上的一等公民（`ToolCallingAdvisor`），记忆与观测可以明确放在循环内侧或外侧。

**加载机制**

- 框架默认注册部分 Advisor（如 ToolCalling）
- 业务用 `.defaultAdvisors(...)` 或本轮 `.advisors(...)` 追加
- 本仓库：`MessageChatMemoryAdvisor.builder(aiChatMemory).build()`

---

### 6.2 `MessageChatMemoryAdvisor`

**干什么**

按 `conversationId` 在调用前加载历史，调用后追加本轮 user / assistant。

**工作原理**

1. 从 Advisor 参数取 `ChatMemory.CONVERSATION_ID`
2. `ChatMemory.get(conversationId)` → 把历史消息并入当前 Prompt
3. 模型返回后 `ChatMemory.add(...)` 写回

无 conversationId 则本轮无跨请求记忆。

---

### 6.3 `ToolCallingAdvisor`

**干什么**

检测模型返回的 tool_calls → 执行对应 `ToolCallback` → 把结果作为 Tool 消息回灌 → 再调模型，直到不再请求工具或达到上限。

**工作原理**

```text
模型：我要调 searchBooks(keyword=Redis)
  → 反射/调度执行 @Tool 方法
  → 把 JSON 结果塞回消息列表
  → 再调 ChatModel
  → 模型组织最终中文回复
```

业务一般**不手写**该类；注册 `.defaultTools(...)` 后由 2.0 默认链路处理。

---

### 6.4 `QuestionAnswerAdvisor`（可选）

**干什么**

经典 RAG：检索 `VectorStore`，把命中文档拼进 Prompt 再生成。

本仓库 FAQ / 书目语义召回主要走 **Tool 内召回**（`SemanticFaqRecaller` / `SemanticBookRecaller`），不是挂 QA Advisor；知道该类即可，便于对比「Advisor 注入 vs Tool 内检索」两种风格。

---

## 七、记忆

### 7.1 `ChatMemory`

**干什么**

多轮对话的「短期记忆」门面：按会话 ID 增删查消息，并落实窗口策略。

**工作原理**

接口方法语义上是：按 `conversationId` 读写 `List<Message>`。具体截断策略由实现类决定。

---

### 7.2 `MessageWindowChatMemory`

**干什么**

滑动窗口实现：只保留最近 `maxMessages` 条，防止上下文无限膨胀。

本仓库：

```java
MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(maxHistoryTurns * 2)  // 一轮 ≈ user + assistant
        .build();
```

**工作原理**

写入时经 Repository 持久化；读取时取窗口内消息。乘 2 是因为一轮对话通常两条 `Message`。

---

### 7.3 `ChatMemoryRepository`

**干什么**

记忆的持久化 SPI：内存、JDBC、Redis 等。与窗口策略解耦——换存储不必改 Advisor。

**工作原理**

核心方法：`findByConversationId` / `saveAll` / `deleteByConversationId`（以你使用的版本接口为准）。

本仓库 `RedisStringChatMemoryRepository`：用 `StringRedisTemplate` 存 JSON 行，兼容普通 Redis（不必 Redis Stack）。

**加载机制**

- 官方若有 Redis Stack 专用实现，可自动配置
- 本仓库**手写** `@Component` 实现接口，再注入 `MessageWindowChatMemory`
- 仍挂同一套 `MessageChatMemoryAdvisor`，换存储不换编排

---

## 八、工具（Tool Calling）

### 8.1 `@Tool` / `@ToolParam`

**干什么**

声明式把 Spring Bean 方法暴露成模型可调用的函数。`description` 既给人看，也给模型选工具用。

**工作原理**

1. 启动或注册时扫描 `@Tool` 方法
2. 用反射生成 JSON Schema（参数名、类型、是否 required、描述）
3. Schema 随请求发给模型
4. 模型返回 tool call → `MethodToolCallback` 按参数名绑定并 `invoke`
5. 返回值序列化后回灌

本仓库示例：`BookSearchTool.searchBooks`、`FaqTool`、`BorrowTool` 等；敏感 `userId` **不**放 `@ToolParam`，而从 `AiUserContext` 读取。

**加载机制**

Tool 类是普通 `@Component`；`ChatClient.Builder.defaultTools(bean...)` 时框架把 Bean 上的 `@Tool` 方法转成 `ToolCallback` 列表。不是 classpath 扫描全局任意类，而是**你显式注册进 ChatClient 的那些 Bean**。

---

### 8.2 `ToolCallback` / `MethodToolCallback`

**干什么**

工具的统一执行接口。注解方式最终也会变成 `ToolCallback`；也可手写回调对接非注解 API。

**工作原理**

`getToolDefinition()` 提供 name / description / inputSchema；`call(arguments)` 执行并返回字符串结果。`ToolCallingAdvisor` 只依赖此抽象，不关心背后是反射还是 HTTP。

---

## 九、向量与 RAG

### 9.1 `Document`

**干什么**

向量库中的一条可检索文档：`id` + `text` + `metadata`。

**工作原理**

业务对象 → `Document.builder()...build()` → `VectorStore.add` → 内部 Embedding → 落 Milvus。检索命中后再从 metadata 还原业务 ID。

本仓库：`BookDocumentBuilder` / `FaqDocumentBuilder`；FAQ 与书目共用 collection，用 metadata `type` 区分。

---

### 9.2 `VectorStore`

**干什么**

向量存储抽象：`add` / `delete` / `similaritySearch`。

**工作原理**

```text
写入：Document → EmbeddingModel → 向量 + payload → Milvus
检索：query 文本 → 向量 → ANN 搜索 → List<Document>
```

本仓库：`BookEmbeddingIndexer`、`SemanticBookRecaller`、`SemanticFaqRecaller` 均注入 `VectorStore`。

**加载机制**

- 依赖 `spring-ai-starter-vector-store-milvus`
- 默认自动配置会被 `spring.autoconfigure.exclude` 屏蔽
- 仅当 `ai.rag.enabled=true` 时，`AiVectorConfig` `@Import(MilvusVectorStoreAutoConfiguration.class)` 显式打开
- 下游用 `@ConditionalOnBean(VectorStore.class)`，RAG 关则语义召回 Bean 不创建，Tool 降级为规则 / SQL

---

### 9.3 `SearchRequest`

**干什么**

相似度检索的请求参数：query 文本、topK、相似度阈值、过滤表达式等。

**工作原理**

`vectorStore.similaritySearch(SearchRequest.builder().query(...).topK(...).build())` → 命中 `Document` 列表。本仓库召回器据此取书 ID / FAQ 条目。

---

## 十、加载机制串讲（Boot + Spring AI）

### 10.1 自动配置如何进来

```text
pom
  spring-ai-bom
  spring-ai-starter-model-openai
  spring-ai-starter-vector-store-milvus
        │
        ▼
AutoConfiguration.imports
        │
        ▼
条件注解（classpath / 配置属性 / 已有 Bean）
        │
        ▼
注册 ChatModel、EmbeddingModel、ChatClient.Builder、
    （可选）VectorStore 等
        │
        ▼
业务 @Configuration（AiChatConfig / AiVectorConfig）
  再组装 ChatMemory、ChatClient、条件 Import Milvus
```

要点：

1. **Starter 负责「能连上模型 / 向量库」**
2. **业务 Config 负责「怎么编排」**（Prompt、Tool、Memory、开关）
3. **条件装配**让 AI / RAG 可整体关闭而不拖垮主应用

### 10.2 本仓库开关一览

| 开关 | 效果 |
| --- | --- |
| `ai.enabled` | 控制 `AiChatConfig` 等对话相关 Bean |
| `ai.rag.enabled` | 控制是否 `@Import` Milvus 自动配置与索引 / 召回 Bean |
| `spring.ai.openai.*` | API Key、地址、Chat / Embedding 模型名 |

### 10.3 一次完整请求里类如何协作

```text
AiChatController
  → AiChatService（校验、conversationId、AiUserContext）
  → ChatClient.prompt().user().advisors(CONVERSATION_ID).call()
       → MessageChatMemoryAdvisor：ChatMemory ← ChatMemoryRepository(Redis)
       → ToolCallingAdvisor + ChatModel：可能多轮
            → @Tool（BookSearchTool / FaqTool / ...）→ 业务 Service
       → MessageChatMemoryAdvisor：写回本轮消息
  → ChatCardCollector.drain() 组装前端卡片
  → 返回 DTO
```

---

## 十一、速查表

| 类 / 注解 | 你何时用它 | 本仓库落点 |
| --- | --- | --- |
| `ChatClient` | 发对话 | `AiChatConfig` / `AiChatService` |
| `ChatClient.Builder` | 装配默认行为 | `AiChatConfig` 注入 |
| `ChatModel` | 换模型、排底层故障 | OpenAI starter 自动配置 |
| `EmbeddingModel` | 理解向量从哪来 | 经 `VectorStore` 间接使用 |
| `Prompt` / `ChatOptions` | 理解请求形态 | 框架内部组装 |
| `ChatResponse` | 拿全文案或元数据 | `.call().content()` |
| `Message` 族 | 记忆序列化 | `RedisStringChatMemoryRepository` |
| `MessageChatMemoryAdvisor` | 多轮会话 | `AiChatConfig` |
| `ToolCallingAdvisor` | 理解自动 Tool 循环 | 2.0 默认链路 |
| `ChatMemory` | 窗口策略 | `MessageWindowChatMemory` |
| `ChatMemoryRepository` | 换存储 | 自研 Redis 实现 |
| `@Tool` / `@ToolParam` | 暴露业务能力 | `com.zx.ai.tool` |
| `ToolCallback` | 非注解工具或源码阅读 | 框架内部 |
| `Document` | 建索引文档 | `*DocumentBuilder` |
| `VectorStore` | 写入 / 检索 | Indexer + Recaller |
| `SearchRequest` | 调 topK / 阈值 | `Semantic*Recaller` |

---

## 十二、和「MCP」的关系（边界）

| | Spring AI `@Tool` | MCP Tool |
| --- | --- | --- |
| 形态 | 同进程 Java 方法 | 跨进程标准协议 |
| 本仓库主路径 | ✅ 使用中 | 未作为业务主路径 |
| Spring AI 支持 | 原生 | 另有 MCP Client / Server Starter |

管理外部信息时：**权威业务事实优先本地 `@Tool`**；需要标准化接入第三方或对外暴露给 Cursor 等 Agent 时再考虑 MCP。细节见对话沉淀与官方 [MCP Overview](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)。

---

## 十三、建议阅读顺序

1. 本文第二～九节：建立类地图
2. 打开 `AiChatConfig` + `AiChatService`：对照装配与一次调用
3. 打开任意 `*Tool` + `RedisStringChatMemoryRepository`：对照 Tool 与 Memory
4. 打开 `AiVectorConfig` + `SemanticBookRecaller`：对照条件加载与 RAG
5. 回读 [Spring AI 框架核心内容学习文档](./SpringAI框架核心内容学习文档.md) 做体系化复习

---

## 参考

- 本仓库 `pom.xml`：`spring-ai.version`、`spring-ai-starter-model-openai`、`spring-ai-starter-vector-store-milvus`
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [AI 模块学习文档](../AI模块学习文档.md)
