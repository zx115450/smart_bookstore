# 学习路线：AI 工程


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)

本路线围绕「Prompt / Tool 评测、RAG 召回质量、幻觉与成本」，以本仓库 Spring AI 客服为锚点，目标是从「能调通模型」进到「能量化是否答对、能否控成本」。

---

## 学习目标

学完后应能：

1. 说明为何用 Tool Calling 约束事实查询，以及 System Prompt 与 `@Tool` description 如何分工
2. 搭建一套离线评测集（问题 → 期望工具 / 期望要点），在 CI 中 Mock、在本地可跑真模型
3. 描述 RAG 召回 → 重排 → 生成的链路，并指出本项目规则召回降级策略
4. 估算一次对话的 Token / 费用，并提出限流或缩短记忆窗口等降本手段

预计投入：3～5 周（含做评测集）。

---

## 与本项目的映射

| 概念 | 本仓库落点 |
| --- | --- |
| System Prompt + Tools | `AiChatConfig`（强制事实走 Tool） |
| ChatClient 装配 | Tools：`BookSearchTool`、`FaqTool`、`BorrowTool`、`BookRecommendTool` 等 |
| 会话记忆 | `RedisStringChatMemoryRepository`、`MessageWindowChatMemory` |
| RAG / 向量 | Milvus；书目 / FAQ 索引与 admin reindex |
| 推荐编排 | `BookRecommendService`：多路召回、语义可选、个性化、重排 |
| 语义降级 | `ObjectProvider<SemanticBookRecaller>`，RAG 关闭时规则召回 |
| 开关 | `ai.enabled`、`ai.rag.enabled` |

建议先通读：`AiChatConfig`、`AiChatService`、`BookRecommendService`、FAQ / Book DocumentBuilder。

---

## 阶段规划

### A1：对话架构对齐（2～3 天）

理清一层职责：

```text
用户问题 → ChatClient（System Prompt + Memory Advisor + Tool Calling）
         → Tool 查 MySQL / FAQ / 推荐服务
         → 模型仅组织语言，不编造库存与规则
```

回答：

1. 哪些问题必须调 Tool？调错或不调会怎样？
2. 匿名用户查「我的借阅」时，Tool 与 Prompt 如何配合？
3. `maxHistoryTurns` 过大有什么成本与串话风险？

产出：一页「能力边界」说明（能做什么 / 明确拒绝什么）。

### A2：Prompt 与 Tool 契约（3～5 天）

- 对比 System Prompt 条文与各 `@Tool` 的 description，找冲突或遗漏
- 为每个 Tool 列出：入参、出参 JSON 关键字段、失败时 `found=false` 语义
- 练习改写一条 description，观察（本地真模型）是否更稳地触发调用

产出：Tool 契约表（可放在 `docs/` 或测试 fixtures）。

### A3：评测集与自动化（1～2 周）

分层评测（强烈建议拆开）：

| 层级 | 测什么 | 是否调用真模型 |
| --- | --- | --- |
| L0 单测 | DocumentBuilder、关键词、Recommend 分支 | 否 |
| L1 Tool 路径 | Mock ChatClient / 直接调 Service | 否 |
| L2 Prompt 回归 | 固定题集，断言「是否调用某 Tool」或答案含关键字段 | 可选真模型 |
| L3 人工抽检 | 幻觉、语气、拒答是否合理 | 是 |

题集示例字段：

```text
id, question, expect_tools, must_contain, must_not_contain, login_required
```

产出：不少于 20 条 FAQ / 查书 / 借阅 / 推荐题；CI 只跑 L0～L1。

### A4：RAG 召回质量（1 周）

- 书目 / FAQ 如何切分与写入向量库？元数据有哪些？
- `reindex` 失败或向量维度变更如何处理？
- LEARN / SIMILAR：语义召回为空时如何回落到分类 / 热门？
- 评测：给定 query，看 Top-K 是否包含期望 bookId / FAQ id（命中率）

产出：一张「召回命中率」小表（10 个 query 即可起步）。

### A5：幻觉、安全与成本（3～5 天）

| 主题 | 练习 |
| --- | --- |
| 幻觉 | 构造「馆内不存在的书」；断言不得捏造架位 |
| 越权 | 未登录查个人借阅；不得编造订单 |
| 成本 | 统计每轮消息数、Tool 往返次数；调整窗口与 limit |
| 限流 | 按用户 / IP 限制 `/api/ai/chat`（可与网关结合） |
| 可观测 | 记录 toolName、耗时、token（若网关返回） |

### A6：扩展课题（选做）

- 结构化输出 / JSON mode 减少自由发挥
- Agent 多步规划 vs 当前「单轮 Tool Calling」取舍
- 推荐：从规则 + 共现升级到显式反馈学习（仍先做好评测）

---

## 推荐阅读与练习顺序

1. Spring AI：ChatClient、Advisors、Tool Calling
2. 精读 `AiChatConfig` 的 SYSTEM_PROMPT
3. 补齐 / 扩展 `BookRecommendServiceTest`、FAQ 单测（见测试指南 T4）
4. 建最小评测集 CSV + 本地脚本跑真模型抽检
5. 写口述稿：「我们如何避免客服编造库存」

---

## 能力自检

- [ ] 能说明 Tool 与 Prompt 如何共同抑制幻觉
- [ ] 有可重复的 L0～L1 自动化，不依赖真 API Key
- [ ] 有一份小评测集，并知道如何解读「答得像但事实错」
- [ ] 能讲清 RAG 关闭时推荐如何降级
- [ ] 能提出至少两项降本措施并说明对体验的影响

---

## 面试口述提纲（示例）

1. 智慧书城客服：查书 / 规则 / 借阅必须走 Tool，模型禁止编造  
2. Redis 会话记忆做多轮；窗口限制控制成本与串话  
3. 推荐多路召回，Milvus 可选，关闭则规则召回  
4. 工程上分层评测：CI Mock，定期真模型抽检与召回命中率  
5. 未登录与无数据时显式拒答，避免「假友好幻觉」  

---

## 相关文档

- [后续学习路线](../后续学习路线.md)
- [学习路线：高并发](./高并发.md)
- [测试板块分步实现指南](../测试板块分步实现指南.md) T4
- [README.md](../../README.md) AI 客服模块
