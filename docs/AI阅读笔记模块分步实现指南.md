# 智慧书城 — AI 阅读笔记模块分步实现指南


> **项目**：smart_bookstore（智慧书城） · **文档类型**：学习文档  
> **关联包**：`com.zx.reader`（规划）— 数字阅读 + 笔记 + 学习 Agent  
> **前置**：[AI 模块学习文档](./AI模块学习文档.md) · [AI 客服技术方案](./AI客服技术方案.md) · [书城系统分阶段实施指南](./书城系统分阶段实施指南.md)  
> **索引**：[学习文档中心](./README.md)  
> **双仓总方案**：[双项目协同：媒资平台 × 智慧书城实现方案](../../docs/双项目协同-媒资平台与智慧书城实现方案.md)  
> **本仓开工清单（并行）**：[线上阅读与学习 Agent 板块 B0～B6](./线上阅读与学习Agent板块/00-阅读说明.md)  
> 建议总周期：**3～4 周**（课余）；MVP 可 **1.5 周**（试看 + 手动笔记 + 章节总结）  
> 最后更新：2026-09

---

## 一、写在前面

### 1.1 模块定位

现有 `com.zx.ai` 是**客服型智能体**（查书、FAQ、推荐、借阅查询）。本模块补齐**用户侧学习智能体**：

| 维度 | 现有 AI 客服 | 本模块（阅读学习 Agent） |
| --- | --- | --- |
| 用户目标 | 问规则、找书、要推荐 | 读书、记笔记、总结归纳 |
| 数据归属 | 公共书目 / FAQ | **用户私有笔记**；章节正文在媒资 CHAPTER 对象 |
| 持久化 | Redis 对话记忆（短期） | MySQL 笔记 + 阅读进度（长期） |
| 向量检索 | 书目 metadata / FAQ | 章节 chunk / 用户笔记（可选） |

记忆口诀：**客服 Agent 答「馆里有什么」；学习 Agent 答「我读到了什么、帮我记下来」。**

### 1.2 与现有能力的关系

```text
com.zx.bookstore.catalog   → 实体书元数据（title/author/架位），可关联 ebook
com.zx.ai                    → 复用 ChatClient / Embedding / Milvus / Redis
com.zx.reader（新建）        → 电子书、章节、试看、笔记、StudyAgent
```

**不复用**客服的 `AiChatConfig` System Prompt；学习 Agent 单独建 `StudyAgentConfig`，避免 Prompt 互相污染。

### 1.3 MVP 演示目标（答辩够用）

1. 管理员导入 1 本公版书，自动切章，前 2 章可试看  
2. 用户登录后阅读试看章节，选中段落保存笔记  
3. 侧边栏 AI：「总结本章」→ 生成摘要并写入笔记  
4. 「把我关于本书的 3 条笔记归纳成知识卡片」  
5. 试看章节外内容访问时返回 403 + 引导借阅/购书（文案即可，支付可不做）

---

## 二、实施前检查（Day 0）

### 2.1 依赖的已有能力

| 能力 | 包/组件 | 本模块用途 |
| --- | --- | --- |
| JWT 登录 | `com.zx.auth` | 笔记归属、试看权限、Agent 身份 |
| 图书目录 | `com.zx.bookstore.catalog` | 可选关联 `book_id`，详情页跳转 |
| Spring AI | `com.zx.ai` | ChatClient、Tool、Embedding |
| Milvus | 可选 | 章节 / 笔记 RAG（R4 才必须） |
| Redis | 已有 | 章节总结缓存、阅读进度热数据 |
| Flyway | 已有 | 新表迁移脚本 |
| Lite Media | `video/lite-vod` | 原件上传、切章、按章拉取 |

### 2.2 环境准备

```text
□ JDK 21 + Maven（与主工程一致）
□ MySQL + Redis 已运行
□ DeepSeek API Key（Chat 总结）
□ 通义 DashScope Key（Embedding，R4 RAG 才必须）
□ 可选：Milvus（docker compose --profile milvus up -d）
□ 1～2 本公版 TXT/Markdown 样本书（用于导入测试）
□ Lite VOD / 媒资 API 可访问（切章与读章依赖）
□ Apifox / Postman
```

### 2.3 实施原则

1. **每阶段结束必须可演示**（接口通 + 自测清单打勾）  
2. **先 DB → Entity → Repository → Service → Controller**  
3. **试看权限在服务端截断**：无权限不向媒资拉取该章  
4. **书城不存章节正文**，只存目录 + `chapter_file_id`  
5. **AI 总结结果落库**，同一章节重复总结先查缓存  
6. **不跨阶段堆功能**（R1 未通不做 RAG）  
7. **每阶段更新** `docs/接口文档.md` 阅读笔记章节  

### 2.4 阶段总览

| 阶段 | 名称 | 周期 | 里程碑 |
| --- | --- | --- | --- |
| **R0** | 工程基座 + 数据库 | 1 天 | 包结构 + Flyway 表 + 编译通过 |
| **R1** | 电子书 + 章节 + 试看 | 3～4 天 | 直传媒资切章、同步目录、BFF 读章、试看拦截 |
| **R2** | 阅读进度 + 手动笔记 | 2～3 天 | 划线/笔记 CRUD、进度续读 |
| **R3** | 学习 Agent + 章节总结 | 3～4 天 | StudyAgent 调 Tool 总结并保存笔记 |
| **R4** | RAG + 多笔记归纳 | 3～4 天 | 章节语义检索、笔记合并归纳 |
| **R5** | 书城联动 + 前端 + 收尾 | 3～5 天 | 实体书关联、试看引导、演示页 |

---

## 三、R0：工程基座 + 数据库（第 1 天）

**目标**：`com.zx.reader` 包就绪，Flyway 建表，健康检查接口可访问。

### Step R0.1 包结构与异常（0.5 天）

| 步骤 | 任务 | 产出 |
| --- | --- | --- |
| R0.1.1 | 新建包 `com.zx.reader` | 根包 |
| R0.1.2 | 子包：`controller` / `service` / `repository` / `entity` / `mapper` / `dto` / `exception` | 目录 |
| R0.1.3 | `ReaderException` + 错误码 **51xx / 52xx / 53xx** | 避开客服已占用的 5001～5099 |
| R0.1.4 | `ReaderExceptionHandler` | 统一 JSON 错误 |
| R0.1.5 | `application.yaml` 增加 `reader.*` 配置项（见 R0.3） | 可配置试看策略 |

**规划包结构**：

```text
com.zx.reader
├── config/       ReaderProperties, StudyAgentConfig（R3）
├── controller/   EbookController, NoteController, StudyAgentController
├── service/      EbookService, ChapterService, NoteService, PreviewAccessService
├── agent/        StudyAgentService, StudyTool*（R3）
├── rag/          ChapterDocumentBuilder, ChapterEmbeddingIndexer（R4）
├── repository/   *Repository
├── entity/       EbookBook, EbookChapter, UserNote, ...
├── mapper/       MyBatis-Plus Mapper
├── dto/          请求/响应 DTO
└── exception/    ReaderException, ReaderExceptionHandler
```

### Step R0.2 Flyway 建表（0.5 天）

新建 `src/main/resources/db/migration/V2__reader_schema.sql`（若已有 V2 则顺延版本号）。

**核心表**：

```sql
-- 线上书（可与实体 book 关联）
CREATE TABLE ebook_book (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id           BIGINT UNSIGNED NULL COMMENT '关联 book.id，可为空',
  title             VARCHAR(128) NOT NULL,
  author            VARCHAR(64) NULL,
  cover_url         VARCHAR(512) NULL,
  format            VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/MARKDOWN',
  status            TINYINT NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
  preview_mode      VARCHAR(16) NOT NULL DEFAULT 'CHAPTER'
    COMMENT 'CHAPTER=按章节试看 RATIO=按比例',
  preview_chapters  INT NOT NULL DEFAULT 2 COMMENT '试看前 N 章',
  preview_ratio     DECIMAL(5,4) NULL COMMENT '试看比例 0~1，preview_mode=RATIO 时用',
  total_chapters    INT NOT NULL DEFAULT 0,
  word_count        BIGINT NOT NULL DEFAULT 0,
  source_file_id    VARCHAR(64) NULL COMMENT '媒资 DOCUMENT 原件 fileId',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ebook_book_id (book_id),
  KEY idx_ebook_source (source_file_id),
  KEY idx_ebook_status (status)
) COMMENT='线上电子书';

-- 章节目录（正文在媒资 CHAPTER 对象，本表不存 content_text）
CREATE TABLE ebook_chapter (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  ebook_id          BIGINT UNSIGNED NOT NULL,
  chapter_no        INT NOT NULL COMMENT '从 1 开始',
  title             VARCHAR(128) NOT NULL,
  chapter_file_id   VARCHAR(64) NOT NULL COMMENT 'Lite Media CHAPTER fileId',
  word_count        INT NOT NULL DEFAULT 0,
  is_preview_free   TINYINT NOT NULL DEFAULT 0 COMMENT '1=试看免费',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ebook_chapter (ebook_id, chapter_no),
  UNIQUE KEY uk_chapter_file (chapter_file_id),
  KEY idx_chapter_ebook (ebook_id),
  CONSTRAINT fk_chapter_ebook FOREIGN KEY (ebook_id) REFERENCES ebook_book(id)
) COMMENT='电子书章节目录，正文在媒资';

-- 用户阅读进度
CREATE TABLE ebook_reading_progress (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  ebook_id          BIGINT UNSIGNED NOT NULL,
  chapter_id        BIGINT UNSIGNED NOT NULL,
  char_offset       INT NOT NULL DEFAULT 0 COMMENT '章节内字符偏移',
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_progress_user_ebook (user_id, ebook_id),
  KEY idx_progress_user (user_id),
  CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES auth_user(id),
  CONSTRAINT fk_progress_ebook FOREIGN KEY (ebook_id) REFERENCES ebook_book(id),
  CONSTRAINT fk_progress_chapter FOREIGN KEY (chapter_id) REFERENCES ebook_chapter(id)
) COMMENT='阅读进度';

-- 用户笔记
CREATE TABLE user_note (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  ebook_id          BIGINT UNSIGNED NULL,
  chapter_id        BIGINT UNSIGNED NULL,
  source_type       VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
    COMMENT 'MANUAL/HIGHLIGHT/AI_SUMMARY/AI_MERGE',
  title             VARCHAR(128) NULL,
  content           MEDIUMTEXT NOT NULL,
  quote_text        VARCHAR(1024) NULL COMMENT '引用原文片段',
  tags              VARCHAR(255) NULL COMMENT '逗号分隔或 JSON',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_note_user (user_id),
  KEY idx_note_ebook (ebook_id),
  KEY idx_note_chapter (chapter_id),
  CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
) COMMENT='用户笔记';

-- AI 总结缓存（可选，减少重复调模型）
CREATE TABLE reader_ai_summary_cache (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  cache_key         VARCHAR(128) NOT NULL COMMENT '如 chapter_summary:{chapterId}',
  content           MEDIUMTEXT NOT NULL,
  model             VARCHAR(64) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_summary_cache_key (cache_key)
) COMMENT='AI 总结缓存';
```

### Step R0.3 配置项

在 `application.yaml` 增加（示例）：

```yaml
reader:
  enabled: true
  preview:
    default-mode: CHAPTER
    default-chapters: 2
  agent:
    enabled: true
    max-input-chars: 12000
  summary:
    cache-enabled: true
    cache-ttl-hours: 168
  rag:
    enabled: false
    collection-name: ebook_chapter
    top-k: 6
    similarity-threshold: 0.55
```

### Step R0.4 自测

```text
□ mvn -DskipTests compile 通过
□ 启动后 Flyway 执行 V2 成功
□ GET /api/reader/health（可选）返回 ok
```

---

## 四、R1：电子书 + 章节 + 试看（第 2～5 天）

**目标**：管理员把样本书直传到媒资并切章，书城只同步目录；用户可读试看章，锁定章在服务端拦截且不拉媒资。

### Step R1.1 实体与 Repository（1 天）

| 步骤 | 任务 | 文件示例 |
| --- | --- | --- |
| R1.1.1 | Entity | `EbookBook.java`, `EbookChapter.java`（含 `chapterFileId`，无正文） |
| R1.1.2 | Mapper | `EbookBookMapper`, `EbookChapterMapper` |
| R1.1.3 | Repository | `EbookBookRepository`, `EbookChapterRepository` |
| R1.1.4 | DTO | `EbookResponse`, `ChapterSummaryResponse`, `ChapterContentResponse` |
| R1.1.5 | Client | `LiteMediaClient.listChapters` / `fetchObjectText` |

### Step R1.2 导入：直传媒资 + 同步目录（1 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R1.2.1 | `EbookImportService` | 向媒资申请 DOCUMENT 上传凭证（`splitRule=MARKDOWN` 或 `TXT_CHAPTER`） |
| R1.2.2 | 浏览器直传 MinIO | 书城不接收全书 multipart 进业务机 |
| R1.2.3 | commit + 轮询 | `POST /vod/medias` 后轮询 `GET /vod/medias/{fileId}/chapters` |
| R1.2.4 | 同步 TOC | 写入 `ebook_chapter`：title、chapter_no、word_count、`chapter_file_id` |
| R1.2.5 | 试看标记 | 按 `preview_chapters` 将前 N 章 `is_preview_free=1` |

切章规则在**媒资 Worker**执行，与总方案第六章一致。书城**禁止**把切章结果写入 `MEDIUMTEXT`。

**管理端接口**：

```text
POST /api/reader/admin/ebooks              ADMIN  创建电子书元数据
POST /api/reader/admin/ebooks/{id}/import  ADMIN  绑定 sourceFileId 并同步目录（文件已直传）
PUT  /api/reader/admin/ebooks/{id}         ADMIN  修改试看策略
GET  /api/reader/admin/ebooks              ADMIN  分页列表
```

### Step R1.3 试看权限服务（0.5 天）

`PreviewAccessService` 核心逻辑：

```java
// 伪代码 — 服务端必须截断
boolean canReadFull(Long userId, EbookBook ebook);
boolean canReadChapter(Long userId, EbookBook ebook, EbookChapter chapter);

ChapterContentResponse getChapterContent(Long userId, Long chapterId) {
    // 1. 查目录行 + 电子书（无正文列）
    // 2. 若无全书权限 && !chapter.isPreviewFree() → throw ReaderException.previewDenied()
    //    此时不得调用 LiteMediaClient.fetchObjectText
    // 3. 有权限 → fetchObjectText(chapter.getChapterFileId()) 组装 JSON
}
```

**试看策略（MVP 只做 CHAPTER 模式）**：

| 模式 | 规则 | MVP |
| --- | --- | --- |
| `CHAPTER` | 前 N 章 `is_preview_free=1` | ✅ 推荐 |
| `RATIO` | 全书前 X% 字数可读 | R5 可选 |
| 借阅/购书解锁 | 查 borrow_order / trade_order | R5 联动 |

### Step R1.4 用户端阅读接口（1 天）

```text
GET  /api/reader/ebooks?page&keyword           USER  电子书列表
GET  /api/reader/ebooks/{id}                   USER  详情 + 试看说明
GET  /api/reader/ebooks/{id}/chapters          USER  章节目录（标注是否可试看）
GET  /api/reader/ebooks/{id}/chapters/{no}     USER  章节正文（含权限校验）
GET  /api/reader/ebooks/{id}/preview-info      USER  { previewMode, freeChapters, ... }
```

**`ChapterSummaryResponse` 字段建议**：`chapterNo`, `title`, `wordCount`, `previewFree`, `locked`（**不含** `chapterFileId` 给普通用户，防绕过 BFF）。

### Step R1.5 种子数据

```text
□ 准备《Redis 设计与实现》节选或公版短文（3～5 章）
□ 管理端直传 + 同步目录成功
□ 前 2 章可读（正文来自媒资），第 3 章返回 403 + 业务码 5103
```

### Step R1.6 自测清单

```text
□ 未登录可读试看章（若产品要求登录后才读，在 Security 配置 USER 角色）
□ 非试看章 GET 正文 → previewDenied
□ 章节目录正确标注 locked / previewFree
□ 更新 docs/接口文档.md R1 章节
```

---

## 五、R2：阅读进度 + 手动笔记（第 6～8 天）

**目标**：用户可保存笔记、划线引用，刷新后从上次位置续读。

### Step R2.1 阅读进度（1 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R2.1.1 | Entity | `EbookReadingProgress` |
| R2.1.2 | `ReadingProgressService` | upsert：`user_id + ebook_id` 唯一 |
| R2.1.3 | 可选 Redis | Key: `reader:progress:{userId}:{ebookId}`，写穿 MySQL |

```text
GET  /api/reader/ebooks/{id}/progress     USER  获取进度
PUT  /api/reader/ebooks/{id}/progress     USER  { chapterId, charOffset }
```

### Step R2.2 笔记 CRUD（1～1.5 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R2.2.1 | Entity | `UserNote` |
| R2.2.2 | `NoteService` | 创建/更新/删除/分页列表 |
| R2.2.3 | 权限 | 仅本人可改；管理员可选只读审计 |
| R2.2.4 | `source_type` | `MANUAL` 手动；`HIGHLIGHT` 带 `quote_text` |

```text
POST   /api/reader/notes                  USER  创建笔记
PUT    /api/reader/notes/{id}             USER  更新
DELETE /api/reader/notes/{id}             USER  删除
GET    /api/reader/notes?ebookId&chapterId USER 分页列表
GET    /api/reader/notes/{id}             USER  详情
```

**创建笔记请求示例**：

```json
{
  "ebookId": 1,
  "chapterId": 2,
  "title": "持久化要点",
  "content": "RDB 是全量快照，AOF 是增量日志",
  "quoteText": "Redis 提供 RDB 和 AOF 两种持久化方式",
  "tags": "redis,持久化"
}
```

### Step R2.3 自测清单

```text
□ 用户 A 无法修改用户 B 的笔记
□ 笔记可按 ebookId / chapterId 筛选
□ 更新阅读进度后再次 GET 返回最新 chapterId + offset
□ 划线笔记 quoteText 正确保存
```

---

## 六、R3：学习 Agent + 章节总结（第 9～12 天）

**目标**：独立 Study Agent，可调 Tool 读章节/笔记、生成总结并写入 `user_note`。

### Step R3.1 与客服 AI 隔离（0.5 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R3.1.1 | `StudyAgentConfig` | 独立 `ChatClient` Bean 名 `studyChatClient` |
| R3.1.2 | System Prompt | 强调「基于原文总结、标注不确定、禁止编造未读内容」 |
| R3.1.3 | 会话键 | `study:user:{userId}:{sessionId}`，与客服 `user:...` 前缀区分 |
| R3.1.4 | `@ConditionalOnProperty` | `reader.agent.enabled=true` |

**System Prompt 要点（示例）**：

```text
你是智慧书城的学习助手，帮助用户阅读、记笔记、总结归纳，使用中文。

【事实来源】涉及章节内容、用户笔记时，必须先调用 getChapterContent / getMyNotes，
只能根据工具返回的内容总结，禁止编造书中未出现的信息。

【保存笔记】用户明确要求「保存」「记下来」时，调用 saveNote 写入笔记库。

【超出范围】未导入的章节、未授权的全书内容，如实说明需借阅或购买。
```

### Step R3.2 Study Tool 列表（1.5 天）

| Tool | 作用 | 依赖 |
| --- | --- | --- |
| `getChapterContent` | 鉴权后从媒资拉章正文 | `PreviewAccessService` + `LiteMediaClient` |
| `getMyNotes` | 查当前用户笔记 | `NoteService` |
| `saveNote` | 保存/更新笔记 | `NoteService` |
| `summarizeChapter` | 总结指定章并可选落库 | Chat + Cache |
| `getReadingProgress` | 续读位置 | `ReadingProgressService` |

**`summarizeChapter` 实现要点**：

1. 查 `reader_ai_summary_cache`，命中则直接返回  
2. 未命中：鉴权通过后 `fetchObjectText` → Prompt「用 5～8 条要点总结」→ 调 ChatClient  
3. 写缓存 + 若 `save=true` 则 `source_type=AI_SUMMARY` 入库  

### Step R3.3 StudyAgentService（1 天）

参照 `AiChatService`：

```text
StudyAgentController
  POST /api/reader/agent/chat
  Body: { "sessionId?", "message", "ebookId?" }
  Response: { "sessionId", "reply", "notes": [...] }  // 可选返回本轮新建笔记 id
```

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R3.3.1 | 登录校验 | 必须 USER 角色（笔记归属用户） |
| R3.3.2 | `AiUserContext` 复用 | 禁止 Tool 参数传 userId |
| R3.3.3 | 消息长度限制 | 如 2000 字 |
| R3.3.4 | 限流 | 复用或新建 `ReaderRateLimiter`（如每用户每小时 20 次） |

### Step R3.4 自测场景

```text
□ 「总结第 2 章」→ 返回要点 + 数据库多一条 AI_SUMMARY 笔记
□ 同一章第二次总结 → 命中 summary_cache，日志可见 skip LLM
□ 「把我刚才的总结保存为笔记」→ saveNote 成功
□ 对锁定章节总结 → Tool 返回无权限，Agent 不编造内容
□ Postman 跑通 POST /api/reader/agent/chat
```

---

## 七、R4：RAG + 多笔记归纳（第 13～16 天）

**目标**：支持「在本书里搜一段讲过的内容」「把多条笔记合并成知识卡片」。

### Step R4.1 章节向量索引（1.5 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R4.1.1 | `ChapterDocumentBuilder` | 按章从媒资拉文本，再按 500～800 字 chunk，带 metadata |
| R4.1.2 | Milvus 集合 | `ebook_chapter`，字段：`ebookId`, `chapterId`, `chunkIndex` |
| R4.1.3 | `ChapterEmbeddingIndexer` | 管理端 `POST .../reindex` 全量重建 |
| R4.1.4 | 开关 | `reader.rag.enabled=false` 时降级为 MySQL `LIKE`（仅 Demo） |

**metadata 示例**：

```json
{
  "ebookId": "1",
  "chapterId": "3",
  "chapterNo": "3",
  "title": "持久化",
  "text": "..."
}
```

### Step R4.2 RAG Tool（1 天）

| Tool | 作用 |
| --- | --- |
| `searchInBook` | 向量检索 + 过滤 `ebookId` + 试看权限（只返回用户可读 chunk） |
| `mergeNotes` | 传入 `noteIds[]` → LLM 归纳 → `source_type=AI_MERGE` 保存 |

**`mergeNotes` Prompt 结构**：

```text
以下是用户关于同一本书的多条笔记，请归纳成：
1）核心概念（3～5 条）
2）易错点（如有）
3）可进一步阅读的建议
保留原文引用标注 [笔记#id]
```

### Step R4.3 管理端索引接口

```text
POST /api/reader/admin/ebooks/{id}/reindex   ADMIN  重建该书章节向量
GET  /api/reader/admin/rag/status            ADMIN  集合文档数
```

### Step R4.4 自测清单

```text
□ 「Redis 持久化有几种方式」→ searchInBook 召回相关 chunk
□ 锁定章节的 chunk 不对未授权用户返回
□ 选 3 条笔记 merge → 生成 AI_MERGE 笔记
□ reader.rag.enabled=false 时不强连 Milvus，服务可启动
```

---

## 八、R5：书城联动 + 前端 + 收尾（第 17～21 天）

**目标**：与实体书、借阅/购书闭环；阅读器页面可演示。

### Step R5.1 与实体书关联（1 天）

| 步骤 | 任务 | 说明 |
| --- | --- | --- |
| R5.1.1 | `ebook_book.book_id` | 关联 `book.id` |
| R5.1.2 | 图书详情 API 扩展 | 返回 `ebookId`、`previewChapters` |
| R5.1.3 | 试看结束引导 | 403 响应带 `action: BORROW / BUY` |

### Step R5.2 全书权限（可选，1 天）

`PreviewAccessService.canReadFull` 扩展：

```text
□ 借阅中 borrow_order.status=BORROWED 且 book_id 匹配
□ 或 trade_order PAID 且含该书
□ 或管理员白名单（测试用）
```

### Step R5.3 阅读画像扩展（可选，0.5 天）

扩展 `UserReadingProfileService` 或新建 `ReaderProfileService`：

- 最近阅读电子书  
- 笔记数量、最近 AI 总结  
- 供客服 Agent `recommendBooks` 或学习 Agent 个性化引用  

### Step R5.4 前端页面（2～3 天）

| 页面 | 功能 |
| --- | --- |
| 电子书列表 | 封面、试看标签 |
| 阅读器 | 章节目录、正文、进度保存、选中划线 |
| 笔记面板 | 列表、编辑、标签筛选 |
| AI 侧边栏 | 对话、一键总结本章、合并笔记 |

可参考 [前端生成提示词](./前端生成提示词.md) 新增「阅读笔记」章节 Prompt。

### Step R5.5 文档与答辩材料

```text
□ 更新 docs/接口文档.md 全文
□ 更新 docs/README.md 索引（已完成则跳过）
□ 准备 3 分钟演示脚本：导入书 → 试看 → 笔记 → AI 总结 → 归纳
□ 准备 1 页架构图：Study Agent + Tool + RAG + 试看权限
```

---

## 九、接口汇总（全阶段）

### 9.1 用户端

```text
# R1 阅读
GET  /api/reader/ebooks
GET  /api/reader/ebooks/{id}
GET  /api/reader/ebooks/{id}/chapters
GET  /api/reader/ebooks/{id}/chapters/{no}
GET  /api/reader/ebooks/{id}/preview-info

# R2 进度与笔记
GET  /api/reader/ebooks/{id}/progress
PUT  /api/reader/ebooks/{id}/progress
POST /api/reader/notes
PUT  /api/reader/notes/{id}
DELETE /api/reader/notes/{id}
GET  /api/reader/notes
GET  /api/reader/notes/{id}

# R3 Agent
POST /api/reader/agent/chat

# R4（Tool 内部，也可暴露便捷 API）
POST /api/reader/notes/merge        Body: { noteIds: [1,2,3] }
POST /api/reader/ebooks/{id}/search Body: { query: "持久化" }
```

### 9.2 管理端

```text
POST /api/reader/admin/ebooks
POST /api/reader/admin/ebooks/{id}/import
PUT  /api/reader/admin/ebooks/{id}
GET  /api/reader/admin/ebooks
POST /api/reader/admin/ebooks/{id}/reindex
```

---

## 十、错误码规划（5xxx）

| 码 | 含义 |
| --- | --- |
| 5101 | 电子书不存在或已下架 |
| 5102 | 章节不存在 |
| 5103 | 试看权限不足（previewDenied） |
| 5201 | 笔记不存在或无权访问 |
| 5301 | Agent 请求过于频繁 |
| 5302 | 章节内容过长，无法一次总结 |
| 5303 | RAG 服务未启用 |
| 5108 | 导入格式无法解析 |

`5001～5099` 已用于 AI 客服（见 `ErrorCode`），阅读模块不要占用。与媒资绑定错误码 `6001～6099` 见工作区总方案。

---

## 十一、安全与成本

### 11.1 安全

- 所有笔记接口校验 `userId` 归属  
- 章节正文**永不**进书城库、**永不**在列表接口返回；详情接口先鉴权再 BFF 拉媒资  
- Agent Tool 禁止 LLM 传入 `userId`，用 `AiUserContext`  
- 管理端原件大小限制在媒资上传凭证侧（如 5MB）与章节数上限  

### 11.2 成本控制

| 手段 | 说明 |
| --- | --- |
| 总结缓存 | `reader_ai_summary_cache` + Redis TTL |
| 分段总结 | 超长章先 Map-Reduce 再合并 |
| 限流 | 每用户每小时 Agent 调用上限 |
| RAG 可选 | 本地开发 `reader.rag.enabled=false` |

---

## 十二、常见坑

| 坑 | 对策 |
| --- | --- |
| 试看在前端隐藏，后端仍拉全书/该章 | 锁定章禁止 `fetchObjectText` |
| 章正文写入书城 `content_text` | 只存 `chapter_file_id`，见工作区总方案 |
| 客服 Agent 与学习 Agent 共用 Prompt | 独立 `StudyAgentConfig` |
| 每次总结都调 LLM | 加 `summary_cache` |
| Milvus 未启动导致整体启动失败 | RAG 用 `@ConditionalOnProperty`，与现有 `ai.rag` 同样模式 |
| 切章规则不适配样本书 | 支持手动指定分隔正则；MVP 只用 Markdown |
| 笔记与聊天历史混淆 | 笔记存 MySQL；Agent 会话存 Redis，职责分离 |

---

## 十三、推荐阅读顺序

1. [AI 模块学习文档](./AI模块学习文档.md) — 理解 ChatClient / Tool / Memory  
2. [AI 模块分板块实施流程](./AI模块分板块实施流程.md) — 对照客服 Agent 落地方式  
3. [Spring AI 框架核心内容学习文档](./learning/SpringAI框架核心内容学习文档.md)  
4. 本文 — 按 R0→R5 实施  
5. [向量检索策略与存储单元](./向量检索策略与存储单元.md) — R4 前阅读  

---

## 十四、里程碑检查表（总览）

```text
R0 □ 包结构  □ Flyway V2  □ reader 配置
R1 □ 直传 DOCUMENT  □ 同步 TOC  □ 试看拦截不拉媒资  □ BFF GET 正文
R2 □ 笔记 CRUD  □ 阅读进度  □ 权限隔离
R3 □ StudyAgentConfig  □ 5 个 Tool  □ 章节总结落库  □ 总结缓存
R4 □ 章节 reindex  □ searchInBook  □ mergeNotes  □ RAG 开关
R5 □ 关联 book_id  □ 前端阅读器  □ 演示脚本  □ 接口文档更新
```

---

**维护提示**：代码落地后请同步更新本文「当前落地进度」小节（可仿 [AI 模块分板块实施流程](./AI模块分板块实施流程.md) 第〇节格式）。
