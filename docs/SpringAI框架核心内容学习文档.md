# Spring AI 2.0 核心：ChatClient、Advisor、Tool、VectorStore

> **项目**：smart_bookstore（智慧书城）  
> **文档类型**：学习文档 · 关联本仓库业务代码  
> **关联包 / 模块**：`com.zx.ai.config`  
> **建议前置**：[AI 模块学习文档](./AI模块学习文档.md)

## 学习目标

1. 理解 ChatClient Builder 与 Advisor 链
2. 掌握 @Tool 声明式工具与 Tool Calling 循环
3. 了解 VectorStore / ChatMemory 与条件装配

## 与本仓库的对应关系

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/zx/ai/config/AiChatConfig.java` | 对照阅读 |
| `src/main/java/com/zx/ai/config/AiVectorConfig.java` | 对照阅读 |
| `src/main/java/com/zx/ai/memory/RedisStringChatMemoryRepository.java` | 对照阅读 |

读理论时请打开上表文件对照；改代码前先跑通 `docker compose up -d` 与 `local` profile。

---

## 导读
对 Java 团队来说，接入大模型常见两条路：

1. 自己用 `WebClient` / OkHttp 调各家 Chat Completions API；  
2. 引入 **Spring AI**，用统一抽象对接模型、工具、记忆与向量库。

前者上手快，但一旦要做 **多轮会话、Tool Calling、流式输出、可观测、换模型、RAG**，胶水代码会迅速膨胀。  
Spring AI 的价值不在「多包一层 HTTP」，而在把 AI 应用常见能力收成 **可组合的 Spring Bean 与 Advisor 链**——和你写 Web、Security、Data 时的心智模型一致。

一句话概括框架定位：

> **用 Spring 的方式编排 LLM：模型可替换，能力可插拔，业务事实走 Tool，语义检索走 VectorStore。**

下面按「它解决什么 → 核心抽象 → 关键能力 → 典型装配 → 落地注意」展开。

---

## 一、Spring AI 解决什么问题

| 痛点 | 没有框架时 | Spring AI 怎么解 |
|------|------------|------------------|
| 换模型 / 换厂商 | 改请求体、鉴权、流式协议 | `ChatModel` / OpenAI 兼容 starter，改配置即可 |
| Tool Calling 多轮循环 | 自己写 while：模型要调工具 → 执行 → 回灌 → 再调 | 2.0 用 `ToolCallingAdvisor` 纳入 Advisor 链 |
| 多轮对话记忆 | 自己拼 messages、截断、落库 | `ChatMemory` + `MessageChatMemoryAdvisor` |
| RAG | 自管 Embedding、切分、检索、拼 Prompt | `Document` + `EmbeddingModel` + `VectorStore`（+ 可选 QA Advisor） |
| 与业务集成 | 散落的 HTTP 与 JSON | `@Tool` 直接调 Service，和普通 Spring Bean 一样 |

它**不是**业务中台，也**不替代**你的 SQL / 规则引擎；它是 **LLM 编排层**。业务正确性仍应由 Tool、鉴权与权威数据源保证。

---

## 二、分层心智图：先记住这五层

```text
┌────────────────────────────────────────────────────────────┐
│  应用层：Controller / Service（会话 ID、鉴权、限流、卡片组装） │
└────────────────────────────┬───────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────┐
│  编排层：ChatClient（fluent API）                            │
│  System Prompt · Tools · Advisors · Options                │
└────────────────────────────┬───────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────┐
│  Advisor 链：Memory / ToolCalling / RAG / 自定义横切逻辑     │
└────────────────────────────┬───────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────┐
│  模型层：ChatModel / EmbeddingModel（OpenAI 兼容等）         │
└────────────────────────────┬───────────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────────┐
│  基础设施：ChatMemoryRepository · VectorStore · MCP Server  │
└────────────────────────────────────────────────────────────┘
```

日常写业务，**90% 时间待在 ChatClient + Tool + Advisor**；只有做索引重建、换向量库时，才深入 `Document` / `VectorStore`。

---

## 三、核心抽象一览

### 3.1 ChatModel：和「某一家模型」对话的最低层

`ChatModel` 负责把 `Prompt`（消息列表 + 选项）发给模型，返回 `ChatResponse`。  
一般**不必手写调用**，由自动配置提供实现（如 OpenAI 兼容协议下的 DeepSeek、通义等）。

你更常接触的是上层 `ChatClient`，但要理解：

- **Chat** 与 **Embedding** 是两条线：对话用 `ChatModel`，向量化用 `EmbeddingModel`；  
- 同一 starter 可配不同 `base-url` / `api-key` / `model`（例如 Chat 走 DeepSeek，Embedding 走 DashScope）。

### 3.2 ChatClient：推荐的日常入口

`ChatClient` 是 Spring AI 的「门面」：链式拼 Prompt、挂 Tools、挂 Advisors，然后 `.call()` 或 `.stream()`。

最小闭环：

```java
String reply = chatClient.prompt()
        .user("有没有 Redis 相关的书？")
        .call()
        .content();
```

生产里通常在配置类里建好「带默认行为」的 Bean：

```java
@Bean
ChatClient aiChatClient(ChatClient.Builder builder, ChatMemory chatMemory, /* tools... */) {
    return builder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultTools(bookSearchTool, faqTool /* ... */)
            .build();
}
```

业务 Service 里只关心「本轮用户说了什么」和「会话 ID」：

```java
String reply = aiChatClient.prompt()
        .user(message)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
```

要点：

| API | 作用 |
|-----|------|
| `defaultSystem` / `.system(...)` | 系统角色与硬规则（何时必须调 Tool、禁止编造等） |
| `defaultTools` / `.tools(...)` | 注册可被模型调用的业务能力 |
| `defaultAdvisors` / `.advisors(...)` | 挂记忆、RAG、审计、限流等横切逻辑 |
| `.call()` / `.stream()` | 同步拿完整回复 / 流式推 token |
| `.entity(SomeDto.class)` | 结构化输出（让模型填 JSON → 映射为 Java 对象） |

### 3.3 Prompt / Message：对话的数据结构

一轮对话本质是消息列表：

- `SystemMessage`：角色与约束  
- `UserMessage`：用户输入  
- `AssistantMessage`：模型回复（可能含 tool_calls）  
- `ToolResponseMessage`（或等价结构）：工具执行结果回灌  

`ChatMemory` 存的就是这些消息；Advisor 在真正调模型前，会把历史拼进当前 Prompt。

### 3.4 Advisor：AI 版的「过滤器链」

Advisor 拦截 `ChatClient` 请求/响应，可在调用模型前后：

- 注入历史消息（Memory）；  
- 执行 Tool 循环（2.0 的 `ToolCallingAdvisor`）；  
- 检索向量库并增强 Prompt（RAG）；  
- 打点、审计、重试、护栏。

心智模型对齐 Spring MVC 的 Filter / Interceptor：**有序链 + 可组合 + 可自定义**。

Spring AI 2.0 的关键变化：**Tool Calling 循环被提升为 Advisor 链上的一等公民**（`ToolCallingAdvisor` 默认自动注册）。  
好处是：记忆、观测、自定义逻辑可以明确放在「Tool 循环内侧或外侧」，而不是藏在某个模型实现的私有 while 里。

常见内置 / 常用 Advisor：

| Advisor | 做什么 |
|---------|--------|
| `MessageChatMemoryAdvisor` | 按 `conversationId` 读写多轮消息 |
| `ToolCallingAdvisor` | 模型要调工具时自动执行并回灌，直到无需再调 |
| `QuestionAnswerAdvisor` 等 | 检索 VectorStore，把相关文档塞进 Prompt（经典 RAG） |
| 自定义 `CallAdvisor` | 日志、租户隔离、敏感词、业务卡片收集等 |

### 3.5 Tool：把「事实」交给业务代码

Tool Calling（Function Calling）是企业场景的**主路径**：

```text
用户自然语言
  → 模型理解意图并选择 Tool + 参数
  → Spring AI 反射/调度执行 @Tool 方法
  → 结构化结果回灌模型
  → 模型组织口语化回复
```

定义方式（注解最常见）：

```java
@Component
public class BookSearchTool {

    @Tool(
        name = "searchBooks",
        description = "按书名、作者或关键词检索上架图书，返回架位与库存。查书时必须先调用。"
    )
    public Map<String, Object> searchBooks(
            @ToolParam(description = "书名或关键词") String keyword,
            @ToolParam(required = false, description = "最多返回几本") Integer limit
    ) {
        // 内部调已有 BookCatalogService / SQL，返回 JSON 友好结构
        return Map.of("found", true, "books", List.of(/* ... */));
    }
}
```

设计原则（比 API 本身更重要）：

1. **description 写给人看，也写给模型看**：说清「何时必须调用」；  
2. **返回权威 JSON**（`found`、业务字段），禁止模型在无 Tool 结果时编造事实；  
3. **敏感身份不要进 `@ToolParam`**：`userId` 应从 JWT / `SecurityContext` / ThreadLocal 注入，防止伪造；  
4. Tool 应是**薄适配层**，复用现有 Service，不要在 Tool 里堆业务。

2.0 还提供工具规模变大时的渐进披露（如 `ToolSearchToolCallingAdvisor`）：先不把上百个 Tool schema 全塞进 Prompt，按需检索再暴露——工具很多时再考虑。

### 3.6 ChatMemory：多轮对话的「短期记忆」

抽象分层：

```text
ChatMemory（窗口策略，如 MessageWindowChatMemory）
        ↓
ChatMemoryRepository（持久化：内存 / JDBC / Redis 等）
```

典型装配：

```java
@Bean
ChatMemory aiChatMemory(ChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(12)  // 例如保留最近 6 轮 user+assistant
            .build();
}
```

请求时传入会话键：

```java
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
```

注意：

- **conversationId 设计要含租户/用户维度**（如 `user:{id}:{sessionId}`），避免串会话；  
- 窗口过长费 token，过短丢指代（「那本呢」「第二本呢」）；  
- 官方 Redis Repository 可能依赖 Redis Stack；若只有普通 Redis，可自实现 `ChatMemoryRepository`，**仍挂同一套 Advisor 链路**。

### 3.7 EmbeddingModel + Document + VectorStore：RAG 三件套

语义检索链路：

```text
业务对象 → Document(text + metadata + id)
         → EmbeddingModel 向量化
         → VectorStore.add / similaritySearch
         → 命中文档回查业务库（补库存、架位等实时字段）
```

| 概念 | 职责 |
|------|------|
| `Document` | 一段可检索文本 + metadata（类型、业务主键等） |
| `EmbeddingModel` | text → float[]，维度必须与库表 schema 一致 |
| `VectorStore` | 增删查；实现有 Milvus、PGVector、Redis 等 |

`similaritySearch` 只负责**召回候选**；架位、库存、上下架状态仍应 **ID 回查 MySQL**，否则向量滞后会答错。

两种常见用法：

1. **Advisor 式 RAG**：`QuestionAnswerAdvisor` 自动检索并拼进 Prompt——适合「文档问答」；  
2. **Tool 内 RAG**：Tool 里调 `VectorStore`，再回查业务库——适合「推荐 / FAQ 语义召回」，且更易控制幻觉与卡片结构。

企业客服场景往往更偏向 **2**：事实仍走 Tool，向量只做召回。

### 3.8 结构化输出与 Options

- **Structured Output**：`.entity(OrderSummary.class)` 等，让回复直接映射 DTO（适合抽取、分类、表单填充）；  
- **ChatOptions**：`temperature`、`model`、`maxTokens` 等，可在配置 `spring.ai.openai.chat.options.*` 或调用时覆盖。

客服话术类温度宜偏低（如 `0.2~0.4`），减少胡编；创意写作再拉高。

### 3.9 MCP（Model Context Protocol，扩展向）

Spring AI 对 MCP 有集成能力：把外部 MCP Server 暴露的工具接到同一套 Tool Calling 体系。  
对多数业务系统，**先把内部 `@Tool` 做稳**；跨系统工具互通或 IDE/Agent 生态对接时再上 MCP。

---

## 四、一次请求在框架内怎么走（同步 Chat）

```text
1. Service：校验入参、生成/复用 sessionId → conversationId
2. ChatClient.prompt().user(...).advisors(conversationId).call()
3. Advisor 链（示意，实际 order 可配）：
   ├─ MessageChatMemoryAdvisor：读出历史 messages，写入当前 Prompt
   ├─ （可选）RAG Advisor：检索文档增强 Prompt
   └─ ToolCallingAdvisor：调用 ChatModel
         ├─ 若响应含 tool_calls → 执行 @Tool → 结果入消息 → 再次调用模型（循环）
         └─ 若无 tool_calls → 得到最终 assistant 文本
4. Memory Advisor：把本轮 user/assistant（及必要 tool 轨迹策略）写回 Repository
5. Service：取 .content()，组装业务响应（如前端卡片）
```

这就是为什么「只配了一个 ChatClient Bean」，就能同时具备记忆与多 Tool 编排——**循环和拼装被框架收走了**。

---

## 五、工程装配：BOM、Starter、配置

### 5.1 依赖（BOM 管版本）

```xml
<properties>
    <spring-ai.version>2.0.0</spring-ai.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- 对话 + Embedding（OpenAI 兼容协议） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <!-- 可选：向量库 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-milvus</artifactId>
    </dependency>
</dependencies>
```

按需替换：`spring-ai-starter-model-ollama`、其他 VectorStore starter 等。

### 5.2 配置要点（OpenAI 兼容）

```yaml
spring:
  ai:
    model:
      chat: openai
      embedding: openai
    openai:
      chat:
        api-key: ${DEEPSEEK_API_KEY}
        base-url: https://api.deepseek.com
        options:
          model: deepseek-chat
          temperature: 0.3
      embedding:
        api-key: ${DASHSCOPE_API_KEY}
        base-url: https://dashscope.aliyuncs.com/compatible-mode
        options:
          model: text-embedding-v3
          dimensions: 1024
    vectorstore:
      milvus:
        client:
          host: localhost
          port: 19530
        collection-name: bookstore_book
        embedding-dimension: 1024   # 必须与 Embedding 输出维一致
        metric-type: COSINE
```

实践建议：

- **Chat 与 Embedding 可拆厂商**：协议兼容即可；  
- **维度三方一致**：模型输出维、配置 `dimensions`、向量集合 schema；  
- **RAG / 向量库用开关**：本地没起 Milvus 时不要强连（条件装配 / exclude 自动配置）。

---

## 六、和业务系统的「正确接缝」

Spring AI 管编排，业务系统管事实。推荐边界：

```text
┌──────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  前端 Chat   │────▶│  Chat API       │────▶│  ChatClient      │
│  sessionId   │     │  JWT / 限流     │     │  Prompt + Tools  │
└──────────────┘     └─────────────────┘     └────────┬─────────┘
                                                      │
                      ┌───────────────────────────────┼───────────────────────────────┐
                      ▼                               ▼                               ▼
               @Tool 查书/FAQ                   @Tool 借阅/画像                  VectorStore
               → Catalog/FAQ Service           → 订单 Service（读登录态）         → 仅语义召回
                      │                               │                               │
                      └───────────────────────────────┴──────────▶ MySQL 权威数据 ◀────┘
```

四条原则（和是否用 Spring AI 无关，但用框架时更容易守住）：

1. **事实归 Tool，话术归模型**；  
2. **身份归鉴权上下文，不归模型参数**；  
3. **向量只做召回，实时字段回查业务库**；  
4. **System Prompt 写清强制调 Tool 的规则**，否则模型爱「直接答」。

---

## 七、能力对照：你该先学哪一块

| 优先级 | 能力 | 什么时候需要 |
|--------|------|--------------|
| P0 | ChatClient + System Prompt | 任何对话入口 |
| P0 | `@Tool` + Tool Calling | 查库、下单、规则——一切要准的事实 |
| P0 | ChatMemory + conversationId | 多轮指代、客服会话 |
| P1 | Embedding + VectorStore | 语义推荐、文档/FAQ 模糊匹配 |
| P1 | 流式 `.stream()` | 前端打字机体验 |
| P2 | 结构化输出 `.entity` | 抽取、分类、Agent 中间态 |
| P2 | 自定义 Advisor | 统一审计、租户、护栏 |
| P3 | MCP / 大规模 Tool Search | 跨系统工具、Tool 数量爆炸 |

学习路径建议：**先跑通「Prompt + 一个 Tool + Memory」**，再加第二个 Tool，最后才上 RAG。  
很多失败项目是一上来堆向量库，却没把 Tool 与鉴权做对。

---

## 八、Spring AI 2.0 相对 1.x 你要知道的变化

结合官方演进，落地时抓住三点即可：

1. **Tool 循环进 Advisor 链**：可观测、可插拔、可替换（默认 `ToolCallingAdvisor`）；  
2. **ChatClient 仍是主 API**：业务代码继续 fluent 风格，升级成本主要在依赖与少量 Advisor/Tool 包名；  
3. **生态模块化**：部分厂商向量库 / Memory 实现可能由厂商维护，选 starter 时看清坐标与自动配置条件。

若你从 1.x 迁过来：优先回归「多 Tool 连续调用」和「Memory + Tool 同时开启」两条路径，这两处最容易受循环位置变化影响。

---

## 九、常见踩坑

| 现象 | 常见原因 | 对策 |
|------|----------|------|
| 模型不调 Tool，直接编造 | Prompt 未强制；Tool description 含糊；未 `defaultTools` | 系统提示写死「必须先调」；检查 Bean 是否注册 |
| 调了 Tool 但仍幻觉 | 模型忽略返回；或返回结构不利于引用 | 返回 `found/message` 明确字段；温度调低 |
| 多轮遗忘 | 未传同一 `CONVERSATION_ID`；窗口过小；Repository 未生效 | 前端回传 sessionId；核对 Redis Key/TTL |
| 串会话 / 越权 | conversationId 未带 userId；userId 暴露为 Tool 参数 | ID 隔离；身份只从安全上下文取 |
| Embedding 维度错误 | 模型维 ≠ 配置维 ≠ collection 维 | 三处对齐，重建索引 |
| 引入 VectorStore 后启动失败 | 自动配置强连未启动的 Milvus | `rag.enabled` + 条件装配 / exclude |
| Token 飙升 | 历史过长；Tool 返回太大；System Prompt 过臃肿 | 窗口截断；Tool 只返回必要字段 |

---

## 十、最小可行代码骨架（串起来）

```text
配置：spring-ai-starter-model-openai + api-key/base-url/model
  ↓
AiChatConfig：
  ChatMemory = MessageWindowChatMemory(repository)
  ChatClient = builder
                 .defaultSystem(...)
                 .defaultAdvisors(MessageChatMemoryAdvisor)
                 .defaultTools(YourTool)
                 .build()
  ↓
AiChatService：
  conversationId = userOrAnon + sessionId
  chatClient.prompt().user(msg).advisors(CONVERSATION_ID).call().content()
  ↓
YourTool：
  @Tool 方法 → 调现有 Service → 返回 Map/JSON
```

把这张骨架跑通，就等于掌握了 Spring AI **最核心的 80%**。  
RAG、流式、MCP、多 Agent 都是在这张骨架上加 Advisor、加 Tool、加基础设施。

---

## 十一、总结：一张核心概念卡片

```text
ChatClient          → 你写业务时的主入口（fluent）
ChatModel           → 真正调 LLM 的端口（多由 starter 提供）
Advisor 链          → Memory / Tool 循环 / RAG / 自定义横切
@Tool               → 业务事实与副作用的唯一可靠出口
ChatMemory          → 多轮上下文（窗口 + Repository）
Embedding+VectorStore → 语义召回（可选，且要回查业务库）
BOM + Starter       → 版本对齐与自动配置
```

记住框架的设计取向：

> **编排用 Spring AI，事实用你的 Service；Advisor 管横切，Tool 管能力，VectorStore 管语义，模型只管理解与表达。**

把这四层分清，Spring AI 就不是一堆陌生类名，而是一套和 Spring Boot 同构的 AI 应用编程模型——可演示、可演进、也能在答辩或评审里讲清楚「为什么这样接」。
