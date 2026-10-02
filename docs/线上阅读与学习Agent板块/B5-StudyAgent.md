# B5：Study Agent

> **阶段**：B5 · **周期**：3 天  
> **目标**：独立学习 Agent（总结 / 改写 / 合并）；与客服 Prompt 隔离；限流与总结缓存。  
> **上一篇**：[B4 进度与笔记](./B4-进度与笔记.md)　**下一篇**：[B6 图书视频绑定](./B6-图书视频绑定.md)

---

## 本步要做什么

新建 `StudyAgentConfig` / `studyChatClient`，**禁止**复用客服 `AiChatConfig` 的 System Prompt。

用户通过阅读侧对话接口调用；Tool 访问章文必须走 B3 同一套鉴权。

---

## 能力与 Tool

| Tool | 行为 |
| --- | --- |
| `getChapterContent` | 内部走 `canReadChapter` + `fetchObjectText`；无权限返回明确错误，禁止编造 |
| `getMyNotes` | 仅当前用户笔记 |
| `saveNote` | 写入 `user_note`，`source_type` 按场景 |
| `summarizeChapter` | 总结 → `AI_SUMMARY`；命中 `reader_ai_summary_cache` 可跳过模型 |
| `rewriteNote` | 改写结果**另存** `AI_REWRITE`，不覆盖原文 |
| `mergeNotes` | 多条合并 → `AI_MERGE` |

会话键建议：`study:user:{userId}:{sessionId}`（Redis）。  
限流：每用户每小时 20 次（超限 5301）。  
输入截断：`reader.agent.max-input-chars`（如 12000）。

---

## 接口（建议）

```text
POST /api/reader/agent/chat
  # body: ebookId?, sessionId, message
  # 需登录
```

配置：

```yaml
reader:
  agent:
    enabled: true
    max-input-chars: 12000
```

---

## 约束

```text
1. 锁定章：Tool 返回无权限；Prompt 写明「不得编造未授权正文」
2. 改写必须另存 AI_REWRITE，保留 MANUAL / HIGHLIGHT 原文
3. 客服 /api/ai/chat 回归：总结图书 FAQ 行为不变
4. 成本：总结优先走 reader_ai_summary_cache（cache_key 含 ebook+chapter+模型版本）
```

---

## 验证

```text
1. 「总结第 2 章」→ 笔记 AI_SUMMARY；再请求同章命中缓存（可看日志 / 表）
2. 「把这条笔记改短」→ 新增 AI_REWRITE，原笔记仍在
3. 「合并我关于本书的笔记」→ AI_MERGE
4. 「总结第 3 章」（锁定）→ 无权限，无胡编长文
5. 限流：短时间刷满 → 5301
6. 客服接口抽测仍通
```

---

## 完成标准

- [ ] 独立 Study Agent Bean / Prompt
- [ ] Tool 鉴权与 B3 一致
- [ ] 总结缓存可用
- [ ] rewrite / merge 落库类型正确
- [ ] 限流 5301
- [ ] 客服行为不变

---

## 本步不做

- 章节向量 RAG（可二期）
- 视频字幕总结（B6 后可加 `VIDEO_SUMMARY`）
- 与客服多 Agent 路由合一
