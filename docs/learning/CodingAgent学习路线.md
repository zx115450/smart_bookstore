# 学习路线：Coding Agent（类 Claude Code）

> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档（加深分册）  
> **索引**：[加深方向](./README.md) · [学习文档中心](../README.md)  
> **相关**：本仓已有 [AI 工程](./AI工程.md)（客服 / RAG / 评测）、[Study Agent B5](../线上阅读与学习Agent板块/B5-StudyAgent.md)（阅读总结改写）。本文讲的是另一类产品：**能实时对话并改仓库 / 改内容的工程 Agent**。

目标产品形态对齐 Claude Code、Cursor Agent：用户用自然语言描述需求，Agent 多轮读代码、改文件、跑命令、看结果，直到任务完成或被打断。

---

## 一、先分清：业务 Agent vs Coding Agent

| 维度 | 本仓 AI 客服 / Study Agent | Coding Agent（本文） |
| --- | --- | --- |
| 典型目标 | 查书、总结章节、改写笔记 | 改源码、跑测试、修编译错误 |
| Tool 副作用 | 多为读库 / 写业务表 | 写磁盘、执行 shell、改 git 工作区 |
| 循环形态 | 常是「单轮或多轮 Tool Calling」 | 强依赖 **Agent Loop**（读 → 改 → 跑 → 再读） |
| 上下文 | 会话记忆 + 少量业务片段 | 仓库结构、打开文件、diff、终端输出、诊断 |
| 成功标准 | 事实正确、不越权、不幻觉 | 能编译 / 测试绿 / diff 可 review |
| 风险面 | 越权查借阅、编造库存 | 删文件、泄密、任意命令执行 |

本仓现有能力是 **业务域 Agent** 的好练手场；要做成 Claude Code 同款，还要补 **编排循环、编辑原语、流式 UX、沙箱权限** 四块。

一句话：**Tool Calling 是积木；Coding Agent 是带副作用闭环的操作系统。**

---

## 二、学习目标

学完后应能：

1. 画出 Coding Agent 的最小架构：消息拼装 → 流式调模型 → 解析 tool call → 执行 → 回灌 → 终止条件
2. 设计一套最小工具集（Read / Grep / Edit / Shell），并说清为何不先给「整仓上传」
3. 说明上下文如何压缩（摘要、截断、按需读取），以及为何「先搜再读」比「一次塞满」更稳
4. 实现一种可 review 的编辑方式（Search-Replace 或 Apply Patch），而不是盲目整文件重写
5. 用 SSE / WebSocket 把「模型字流 + 工具进度 + diff」推到前端
6. 列出写文件 / 跑命令的权限与沙箱策略，并知道如何做可中断

预计投入：

| 深度 | 周期 | 产出 |
| --- | --- | --- |
| 原理通关 | 1～2 周 | 能口述架构与和本仓 Agent 的差异 |
| 可跑原型 | 3～5 周 | CLI 或 Web：对话改本地目录里的小项目 |
| 可演示产品 | 6～10 周 | 流式 UI、diff 确认、权限门、简单评测集 |

---

## 三、知识地图（按优先级）

```text
L0 基础          LLM API · JSON Schema · 流式 SSE · 异步取消
L1 核心循环      Tool Calling · Agent Loop · 终止 / 重试 / 并行工具
L2 工程能力      文件读写 · 搜索 · Diff/Patch · Shell · 工作区隔离
L3 上下文        System Prompt · 记忆压缩 · 按需取证 · 错误回灌
L4 产品体验      流式事件协议 · Diff 预览 · Stop · 会话持久化
L5 安全与成本    权限门 · 路径沙箱 · 密钥扫描 · Token / 限流
L6 进阶          子 Agent · MCP · 计划模式 · 评测与回归
```

建议顺序：**L0 → L1 → L2 最小闭环 → L4 一点点体验 → L5 再加固 → L3/L6 持续打磨**。  
常见误区是一上来做「多智能体规划」，却没有稳定的「改一个文件 + 跑通测试」。

---

## 四、分阶段学习计划

### C0：心智模型（1～2 天）

弄清三件事：

1. **模型不会直接改磁盘**：它只输出文本或结构化 tool call；宿主进程执行后把结果当消息塞回
2. **实时感来自流式事件**，不是模型与文件系统「同一时钟」
3. **质量主要靠循环与工具设计**，不只靠换更大模型

对照阅读：

- 本仓：`AiChatConfig`、`StudyAgent` 相关配置（看业务 Tool 怎么声明）
- [Spring AI 框架核心内容](./SpringAI框架核心内容学习文档.md) 中 Tool Calling 循环章节
- [AI 工程](./AI工程.md) A1～A2（Prompt 与 Tool 契约）

产出：一页架构草图（可画在笔记里）：

```text
[UI/CLI]
   │  user + 打开文件 / 选中区
   ▼
[Orchestrator]
   │  messages + tool schemas
   ▼
[LLM API] ──stream tokens──► UI
   │
   │  tool_calls
   ▼
[Tool Runtime] ── 读/写/搜/跑 ──► tool_result
   │
   └──────────────► 再调 LLM …
```

### C1：LLM 调用与 Tool Calling（3～5 天）

要掌握：

- Chat Completions / Responses 类 API：`messages`、`tools`、`tool_choice`
- 流式：`delta.content` 与 `delta.tool_calls`（含分片拼接 arguments）
- Tool 描述：`name`、`description`、`parameters`（JSON Schema）；description 决定「会不会被调对」
- 多 tool 并行：互不依赖的 Read/Grep 可同轮发出

练习：

1. 用任意 SDK（Spring AI / OpenAI Java / Node）写一个 **while 循环**：模型要调工具 → 你执行 → 回灌 → 直到无 tool call
2. 故意写烂 description，观察误调；再改好，对比稳定性
3. 处理流式 tool_call 分片拼 JSON 失败的情况（缓冲、校验、重试一轮）

自检：

- [ ] 能解释「assistant message 里的 tool_calls」和「tool role 的 result」如何成对出现
- [ ] 能处理一次回复里多个 tool call
- [ ] 能 Abort 正在进行的流式请求

### C2：最小 Coding 工具集（1～2 周）

先做 **4 个工具**，不要一上来做 20 个：

| 工具 | 职责 | 设计要点 |
| --- | --- | --- |
| `read_file` | 按路径读文本 | 限大小；大文件支持 offset/limit；二进制拒绝或提示 |
| `search` | 内容 / 文件名搜索 | 对标 ripgrep；返回路径 + 行号 + 上下文；结果截断 |
| `apply_edit` | 改文件 | 优先 Search-Replace 或 unified diff；失败要返回可读错误 |
| `run_shell` | 跑命令 | 工作目录限制在 workspace；超时；stdout/stderr 截断 |

原则：

- **先搜再读再改**：降低幻觉路径
- **编辑必须可验证**：返回「是否匹配到 old_string」「写后摘要」
- **Shell 是最后手段**：能用专用工具就别让模型拼复杂命令

编辑原语对比（必学）：

| 方式 | 优点 | 缺点 |
| --- | --- | --- |
| 整文件 Write | 实现简单 | 大文件贵、易冲掉未读内容 |
| Search-Replace | 精确、好测 | old 必须唯一；格式空白敏感 |
| Unified Diff / Apply Patch | 易 review、工业常用 | 解析与冲突处理更重 |
| AST / LSP 编辑 | 语义稳 | 实现与语言绑定成本高 |

本阶段产出：一个 CLI，对指定目录说「给 `Foo.java` 加一行日志」，Agent 能自己找到文件并改对。

### C3：Agent Loop 工程化（约 1 周）

在 while 循环上补齐产品级细节：

| 主题 | 要会什么 |
| --- | --- |
| 终止条件 | 无 tool call；达最大步数；用户 Stop；连续同类失败 |
| 错误回灌 | 编译失败、测试失败、patch 冲突原文塞回，让模型自修 |
| 步数与预算 | `maxSteps`、`maxTokens`、每用户限流（可对照 `StudyAgentRateLimiter`） |
| 并行工具 | 只并行无副作用读操作；写与 shell 串行或加锁 |
| 可观测 | 每步记录 toolName、耗时、截断前后字节数 |

伪代码骨架：

```text
messages = [system, ...history, user]
for step in 1..maxSteps:
  stream = llm.stream(messages, tools)
  emit text/tool events to UI
  if no tool_calls: break
  for call in tool_calls:          # 读类可并行
    result = run(call) with timeout + sandbox
    messages.append(tool_result(result))
else:
  emit "达到最大步数，已暂停"
```

自检：

- [ ] 故意让编译失败，Agent 能在后续步读日志并再改
- [ ] Stop 后不再继续写文件
- [ ] 单步 tool 超时不会卡死整次会话

### C4：上下文工程（贯穿，单列 3～5 天深化）

Coding Agent 的「智商」很大程度是上下文预算怎么花。

必学技巧：

1. **System Prompt**：角色、工具用法、仓库约定、禁止事项（少而硬，勿长篇散文）
2. **按需取证**：默认不塞整仓；用 search → read 局部
3. **环境注入**：打开文件、光标、git status、linter 诊断（IDE 类产品的关键差异）
4. **历史压缩**：旧 tool 结果只留摘要；长对话做 rolling summary
5. **输出截断策略**：终端 50KB+ 只留头尾 + 中间省略标记，避免爆窗

练习：同一任务分别「塞 3 个大文件」vs「只给路径让模型自己搜」，对比步数与正确率。

### C5：实时对话 UX（3～7 天）

事件协议建议至少区分：

| 事件 | 含义 |
| --- | --- |
| `message.delta` | 模型自然语言增量 |
| `tool.start` / `tool.end` | 工具开始与结束（含摘要结果） |
| `file.diff` | 某路径的 unified diff 或 before/after |
| `status` | thinking / running_tool / done / error |
| `control.aborted` | 用户中断 |

传输：Web 用 **SSE** 或 WebSocket；CLI 用 stderr 日志 + stdout 最终答复即可。

前端最小体验：

- 流式打字
- 工具调用时间线（读了谁、改了谁）
- diff 面板 + 可选「接受 / 拒绝」
- Stop 按钮

本仓可对照：阅读 Agent 的聊天接口形态（`POST /api/reader/agent/chat`），再升级为流式；业务 Agent 与 Coding Agent 可共用「流式壳」，换 Tool Runtime。

### C6：安全、权限、成本（与 C2 同步启动，约 1 周）

没有这层，Coding Agent = 远程任意代码执行。

最低限度：

| 控制点 | 做法 |
| --- | --- |
| 路径沙箱 | 所有读写解析到 workspace 根下；禁 `..` 逃逸 |
| 命令策略 | 默认确认 `rm`、网络、`git push`、改权限；白名单常用命令 |
| 密钥 | 拒绝读写 `.env`、私钥、credential 类路径；结果脱敏 |
| 确认门 | 写文件 / shell 可配置为 auto / ask / deny |
| 限流与配额 | 每用户 QPS、日 Token 上限；对照本仓秒杀令牌桶 / Study 限流思路 |
| 审计 | 会话级 tool 审计日志，便于追责与回放 |

成本侧：

- 能本地做的不要反复调模型（例如格式化用现成工具）
- 缓存「目录树 / 符号索引」减少重复 Grep
- 小模型做路由 / 摘要，大模型做关键编辑（可选）

### C7：评测与回归（约 1 周，可并行）

对标 [AI 工程](./AI工程.md) 的分层评测，Coding Agent 建议：

| 层级 | 测什么 | 是否调真模型 |
| --- | --- | --- |
| L0 | Patch 应用、路径沙箱、截断逻辑 | 否 |
| L1 | Mock LLM 固定 tool_calls 序列，断言文件最终内容 | 否 |
| L2 | 小型真实题集（「修一个 NPE」「加一个 REST 字段」） | 是 |
| L3 | 人工看 diff 质量、有无改飞无关文件 | 是 |

题集字段示例：`repo_fixture`、`prompt`、`expect_files_changed`、`forbid_paths`、`test_command`、`pass_criteria`。

面试友好结论：**先有可重复的 L0/L1，再谈「Agent 很强」。**

### C8：进阶选修（按兴趣）

- **子 Agent**：探索代码的只读 Agent + 主会话改代码，缩短主上下文
- **计划模式**：先出 plan 再执行；大重构更稳
- **MCP**：把外部系统能力建成标准工具协议
- **IDE 深度集成**：LSP 诊断回灌、断点、多文件 refactor
- **记忆与规则**：`AGENTS.md` / 项目规则，持久化团队约定
- **多模型路由**：便宜模型做搜索摘要，强模型做补丁

---

## 五、推荐动手项目（由易到难）

### P1：Tool Loop 玩具（2～3 天）

- 工具：`get_time`、`add`、`echo_file`
- 目标：跑通「流式 + 多轮 tool + 回灌」
- 通过标准：无手工拼接散文式「请调用工具」

### P2：目录级改写 Agent（1～2 周）

- 限定一个 `playground/` 目录
- 工具：Read / Grep / StrReplace / Shell（仅 `javac` 或 `mvn test`）
- 目标：完成 5 个固定小任务题集
- 通过标准：L1 Mock 全绿 + 真模型抽检 ≥ 3/5

### P3：流式 Web 控制台（1～2 周）

- 后端 SSE 推送事件；前端时间线 + diff
- 写操作默认 ask
- 通过标准：Stop 立刻停；拒绝的 diff 不落盘

### P4（选做）：对接本仓业务

两条有区分度的路径：

1. **阅读域**：把 Study Agent 从「总结笔记」扩成「按用户指示改章节 Markdown 草稿」（仍在权限内）
2. **工程域**：独立模块 `com.zx.agentcoding`（或单独小仓），不要和客服 Prompt 混用

原则与 B5 一致：**独立 System Prompt，禁止 Prompt 互相污染。**

---

## 六、技术选型参考（Java 栈友好）

本仓库以 Spring Boot + Spring AI 为主，可参考：

| 能力 | 可选方案 |
| --- | --- |
| 模型访问 | Spring AI `ChatClient`；或官方 HTTP SDK |
| Tool 声明 | `@Tool` / `FunctionCallback`；Coding 工具建议显式 schema |
| Agent 循环 | Spring AI 2.x ToolCallingAdvisor；或自研 while（更易控权限与步数） |
| 流式 | WebFlux SSE / `SseEmitter` |
| 搜索 | 调系统 `rg`，或 Java 实现简化版 |
| 会话 | Redis（与现有 Chat Memory 同思路，键前缀隔离） |
| 限流 | Redis + Lua；模式可参考秒杀令牌桶 / `StudyAgentRateLimiter` |

若用 Node / Python 做原型往往更快；**先验证产品形态，再迁回 Java 并不丢人**。

---

## 七、与本仓库文档的衔接

| 你已有的 | 迁到 Coding Agent 时复用什么 |
| --- | --- |
| [AI 模块学习文档](../AI模块学习文档.md) | Chat 心智模型、读码顺序 |
| [AI 工程](./AI工程.md) | Prompt/Tool 契约、分层评测、成本 |
| [Spring AI 常用类](./SpringAI常用类学习文档.md) | ChatClient / Advisor / Memory |
| [B5 Study Agent](../线上阅读与学习Agent板块/B5-StudyAgent.md) | 独立 Config、限流、输入截断、会话键 |
| 秒杀令牌桶 / 生产限流文档 | 入口配额与防刷 |
| Actuator / Micrometer 文档 | tool 耗时、调用次数、错误率指标 |

差异要单独学：文件系统沙箱、Patch、Shell、IDE 上下文、人机确认门——这些不在客服 Agent 主路径里。

---

## 八、能力自检清单

- [ ] 能徒手画出 Agent Loop，并指出每一步的失败模式
- [ ] 能实现 Read + Search + Edit + Shell 四件套，且路径不能逃出 workspace
- [ ] 至少一种可 review 的编辑方式，冲突时能给模型可读错误
- [ ] 流式协议能区分文本、工具、diff、中断
- [ ] 有 `maxSteps` / 超时 / Stop，避免无限循环烧钱
- [ ] 有 L0/L1 自动化评测，不依赖每次手工点聊天框
- [ ] 能讲清：为何业务 Agent 的 Tool 设计不能直接等同 Coding Agent

---

## 九、面试口述提纲（示例）

1. Coding Agent 本质是 **带工具的多轮控制回路**，模型负责决策，运行时负责副作用
2. 工具集刻意做小：搜索与阅读降低幻觉，编辑用 patch，Shell 受沙箱与确认门约束
3. 上下文按需取证 + 结果截断，比「整仓塞进 Prompt」更稳也更省
4. 实时体验靠 **事件流**（token / tool / diff），并支持取消
5. 质量靠 **错误回灌 + 题集回归**；安全靠路径沙箱与危险操作确认
6. 和智慧书城客服 / Study Agent 的关系：同属 Tool Calling 家族，但成功标准与风险模型不同

---

## 十、建议学习资源类型（按主题找，不绑死某一本书）

| 主题 | 去看什么类型的材料 |
| --- | --- |
| Tool Calling | 你所用模型的官方 Function Calling / Tools 文档 |
| Agent 模式 | ReAct、Tool Use 循环；各家「Agent SDK」示例里的 while loop |
| Diff / Patch | `diff` / `patch` 统一格式；Git diff 阅读练习 |
| 流式 API | SSE 规范；Spring `SseEmitter` 或 WebFlux 示例 |
| 安全 | OWASP 对命令注入、路径遍历的基础条目 |
| 产品对照 | 开源 Coding Agent / CLI 的工具列表与系统提示（学结构，勿整段抄规则当机密） |

学习时以 **自己能复现一个最小闭环** 为准，而不是收集名词。

---

## 相关文档

- [学习路线：AI 工程](./AI工程.md)
- [Spring AI 框架核心内容学习文档](./SpringAI框架核心内容学习文档.md)
- [Spring AI 常用类学习文档](./SpringAI常用类学习文档.md)
- [线上阅读与学习 Agent · B5](../线上阅读与学习Agent板块/B5-StudyAgent.md)
- [AI 模块学习文档](../AI模块学习文档.md)
- [加深方向学习路线](./README.md)
