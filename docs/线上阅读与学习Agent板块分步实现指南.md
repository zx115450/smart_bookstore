# 智慧书城 — 线上阅读与学习 Agent 板块分步实现指南


> **项目**：`smart_bookstore` · **文档类型**：板块实现（本仓独立开工）  
> **分步开工** → **[线上阅读与学习Agent板块/](./线上阅读与学习Agent板块/00-阅读说明.md)**（B0～B6 逐步文档）  
> **关联包**：`com.zx.reader`、`com.zx.media.client`（规划）  
> **总方案**：[双项目协同实现方案](../../docs/双项目协同-媒资平台与智慧书城实现方案.md)  
> **对侧板块**：[媒资平台板块 L0～L5](../../video/docs/16-媒资平台板块/00-阅读说明.md)  
> **原文保留**：[AI 阅读笔记 R0～R5](./AI阅读笔记模块分步实现指南.md) · [AI 客服](./AI模块学习文档.md) · 工作区总方案  
> 建议周期：**2～3 周**（课余）；无真实媒资时用 Mock 可先做完 B0～B5  
> 最后更新：2026-10

---

## 怎么用

实施时请从 [阅读说明](./线上阅读与学习Agent板块/00-阅读说明.md) 按 **B0→B6** 逐步做；本文保留契约、表结构、配置与全局检查表，不替代逐步文档。

| 阶段 | 逐步文档 |
| --- | --- |
| B0 | [Client 与配置](./线上阅读与学习Agent板块/B0-Client与配置.md) |
| B1 | [Flyway 目录表](./线上阅读与学习Agent板块/B1-Flyway目录表.md) |
| B2 | [导入同步 TOC](./线上阅读与学习Agent板块/B2-导入同步TOC.md) |
| B3 | [试看 BFF 读章](./线上阅读与学习Agent板块/B3-试看BFF读章.md) |
| B4 | [进度与笔记](./线上阅读与学习Agent板块/B4-进度与笔记.md) |
| B5 | [Study Agent](./线上阅读与学习Agent板块/B5-StudyAgent.md) |
| B6 | [图书视频绑定](./线上阅读与学习Agent板块/B6-图书视频绑定.md) |
| — | [附录-联调样例](./线上阅读与学习Agent板块/附录-联调样例.md) |

---

## 一、本板块做什么

在书城落地：**线上书目录、试看、笔记、学习 Agent（总结 / 改写 / 合并）**，以及图书配套视频的绑定与播放（production：`/internal/medias/{id}/play-url`）。

正文**不进书城库**。`ebook_chapter` 只存目录 + `chapter_file_id`；读章经 BFF 向媒资拉文本。

| 做 | 不做 |
| --- | --- |
| TOC、试看 5103、借阅解锁 | FFmpeg、MinIO、切章正则执行 |
| 笔记 CRUD、进度 | 持久化章正文 `MEDIUMTEXT` |
| Study Agent（独立 Prompt） | 与客服共用 `AiChatConfig` |
| `LiteMediaClient`（可 Mock） | 直连媒资 MySQL / MinIO |

口诀：**权限在书城，文件在媒资，目录在书城。**

---

## 二、与原文的关系

| 原文 | 用法 |
| --- | --- |
| [AI 阅读笔记模块分步实现指南](./AI阅读笔记模块分步实现指南.md) | R0～R5 细步骤仍可用；**表结构以本文为准**（无 `content_text`） |
| [对接书城 M0～M5](../../video/docs/15-对接智慧书城/01-媒资平台与智慧书城分步实现指南.md) | 绑视频、exper/preview 播放；不要删 |
| 工作区总方案 | 错误码 51xx、端口、P2～P6 |

本文是书城仓的**契约与总检查表**；逐步任务见 [分步目录 B0～B6](./线上阅读与学习Agent板块/00-阅读说明.md)。与媒资仓并行时先对接第四章契约，媒资未就绪则走 Mock。

---

## 三、并行策略

```text
B0～B1  不依赖媒资进程（Client 接口 + Flyway）
B2～B3  Mock listChapters / fetchObjectText 即可演示试看
B4～B5  笔记与 Agent 只依赖 Mock 章文本
B6      真实 GET /internal/medias/{id}/play-url（公开 /vod/signature/play 仅 debug）
B2 切章同步  换成真实 HTTP 当媒资 L3 完成
```

Mock 开关：`bookstore.media.lite-vod.mock=true` 时内存三章样例，不连 `8080`。

---

## 四、依赖的媒资契约（只消费，不实现）

完整字段见对侧 [媒资板块第四章](../../video/docs/16-媒资平台板块分步实现指南.md)。

**验权在书城，媒资只认 Internal-Token（方案 B）。** 生产路径全部走 `/internal/**`；公开 `/vod/signature/*` 仅媒资本地 debug。

```text
# 生产（书城 LiteMediaClient，Header: X-Internal-Token）
GET  /internal/medias/upload-signature?assetType=DOCUMENT
POST /internal/medias                    # commit：filename, fileId, assetType, splitRule
GET  /internal/medias/{fileId}/object-url?ttl=   # 返回短链，BFF 再 GET MinIO

# 元数据（可走公开 /vod，或后续也收口 internal；首期 Client 用 /vod 即可）
GET  /vod/medias/{fileId}
GET  /vod/medias/{fileId}/chapters
GET  /internal/medias/{fileId}/play-url?preview= # 视频播放；用户鉴权在书城后再调 + Token

# 媒资本地 debug（书城生产勿依赖）
GET  /vod/signature/upload?assetType=
POST /vod/medias
GET  /vod/signature/object?fileId=&ttl=
```

`LiteMediaClient` 方法：

| 方法 | 媒资调用 |
| --- | --- |
| `createUploadSignature(assetType)` | `GET /internal/medias/upload-signature` + Token |
| `commit(...)` | `POST /internal/medias` + Token |
| `listChapters` / `getMedia` | `GET /vod/medias/...` |
| `fetchObjectText(fileId)` | `GET /internal/.../object-url` + Token → **再 GET MinIO** |
| `getPlaySignature` | `GET /internal/medias/{id}/play-url` + Token（书城先鉴权） |

超时：connect 3s、read 10s。媒资挂了：阅读正文 6002；借阅等原模块不受影响（`enabled=false` 可启动）。

ADMIN 导入电子书流程：

```text
ADMIN JWT → 书城管理接口鉴权
  → LiteMediaClient.createUploadSignature(DOCUMENT)   # internal + Token
  → 浏览器 PUT uploadUrl（直传 MinIO，不过书城/媒资业务字节）
  → LiteMediaClient.commit(...)                       # internal + Token
  →（异步）媒资切章完成 Webhook → 书城写 ebook_chapter
```

---

## 五、书城数据模型（Flyway）

版本号按现网顺延（当前为 `V1`）。**禁止** `content_text`。

```sql
CREATE TABLE ebook_book (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id           BIGINT UNSIGNED NULL,
  title             VARCHAR(128) NOT NULL,
  author            VARCHAR(64) NULL,
  cover_url         VARCHAR(512) NULL,
  format            VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN',
  status            TINYINT NOT NULL DEFAULT 1,
  preview_mode      VARCHAR(16) NOT NULL DEFAULT 'CHAPTER',
  preview_chapters  INT NOT NULL DEFAULT 2,
  total_chapters    INT NOT NULL DEFAULT 0,
  word_count        BIGINT NOT NULL DEFAULT 0,
  source_file_id    VARCHAR(64) NULL COMMENT '媒资 DOCUMENT',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ebook_book_id (book_id),
  KEY idx_ebook_source (source_file_id)
) COMMENT='线上书元数据';

CREATE TABLE ebook_chapter (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  ebook_id          BIGINT UNSIGNED NOT NULL,
  chapter_no        INT NOT NULL,
  title             VARCHAR(128) NOT NULL,
  chapter_file_id   VARCHAR(64) NOT NULL COMMENT '媒资 CHAPTER',
  word_count        INT NOT NULL DEFAULT 0,
  is_preview_free   TINYINT NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ebook_chapter (ebook_id, chapter_no),
  UNIQUE KEY uk_chapter_file (chapter_file_id),
  CONSTRAINT fk_chapter_ebook FOREIGN KEY (ebook_id) REFERENCES ebook_book(id)
) COMMENT='章节目录，无正文';

CREATE TABLE ebook_reading_progress (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  ebook_id     BIGINT UNSIGNED NOT NULL,
  chapter_id   BIGINT UNSIGNED NOT NULL,
  char_offset  INT NOT NULL DEFAULT 0,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_progress_user_ebook (user_id, ebook_id)
);

CREATE TABLE user_note (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  ebook_id     BIGINT UNSIGNED NULL,
  chapter_id   BIGINT UNSIGNED NULL,
  book_id      BIGINT UNSIGNED NULL,
  file_id      VARCHAR(64) NULL,
  source_type  VARCHAR(16) NOT NULL DEFAULT 'MANUAL'
    COMMENT 'MANUAL/HIGHLIGHT/AI_SUMMARY/AI_REWRITE/AI_MERGE/VIDEO_SUMMARY',
  title        VARCHAR(128) NULL,
  content      MEDIUMTEXT NOT NULL,
  quote_text   VARCHAR(1024) NULL,
  tags         VARCHAR(255) NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_note_user (user_id)
);

CREATE TABLE reader_ai_summary_cache (
  id         BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  cache_key  VARCHAR(128) NOT NULL,
  content    MEDIUMTEXT NOT NULL,
  model      VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_summary_cache_key (cache_key)
);

CREATE TABLE book_media_ref (
  id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  book_id          BIGINT UNSIGNED NOT NULL,
  file_id          VARCHAR(64) NOT NULL,
  title            VARCHAR(128) NULL,
  media_type       VARCHAR(16) NOT NULL DEFAULT 'INTRO',
  preview_seconds  INT NOT NULL DEFAULT 300,
  sort_order       INT NOT NULL DEFAULT 0,
  status           TINYINT NOT NULL DEFAULT 1,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_book_file (book_id, file_id)
);
```

错误码（避开客服 `5001～5099`）：

| 段 | 含义 |
| --- | --- |
| 5101 | 电子书不存在 / 下架 |
| 5103 | 试看拒绝（不调媒资） |
| 5201 | 笔记无权 |
| 5301 | Agent 限流 |
| 6002 | 媒资不可用 |
| 6003 | 章对象不存在或未切完 |

---

## 六、分期（B0～B6）

| 阶段 | 名称 | 周期 | 媒资依赖 |
| --- | --- | --- | --- |
| **B0** | Client + 配置 + 包结构 | 0.5～1 天 | Mock 即可 |
| **B1** | Flyway 目录表 | 0.5 天 | 无 |
| **B2** | 导入同步 TOC | 1～2 天 | Mock 或真实 L3 |
| **B3** | 试看 BFF 读章 | 1～2 天 | `fetchObjectText` |
| **B4** | 进度 + 笔记 | 2 天 | 无（引用章 id 即可） |
| **B5** | Study Agent | 3 天 | 读章同 B3 |
| **B6** | 图书视频绑定 | 1～2 天 | 已有 play 签名 |

### B0

```text
com.zx.media.client.LiteMediaClient
com.zx.reader.* 空包与 ReaderException
bookstore.media.lite-vod.enabled / mock / base-url / internal-token
```

Client **生产默认打 internal**（upload-signature / commit / object-url），请求头带 `internal-token`。  
Mock：固定 `sourceFileId=mock-doc-1`，三章 fileId `mock-c-1..3`，正文写死在测试资源。

### B1

```text
□ V2 执行成功
□ ebook_chapter 无 content_text 列
```

### B2

管理端：创建 `ebook_book` →（真实）**书城鉴权后**经 `LiteMediaClient` 调 internal 拿上传凭证 → 浏览器直传 → internal commit → 写 `source_file_id` → **媒资切章完成后 Webhook** → 书城 `listChapters` 插入目录 → 前 N 章 `is_preview_free=1`。

Mock：跳过直传，直接灌三章 TOC（或手工调回调 / `sync-chapters`）。

```text
POST /api/reader/admin/ebooks
POST /api/reader/admin/ebooks/{id}/sync-chapters   # 补救：按 source_file_id 拉目录
POST /api/internal/media/callback                  # 媒资 Webhook（主路径）
```

注意：浏览器**只**拿 `uploadUrl` 去 PUT MinIO；**不要**让前端直接调媒资 `/vod/signature/upload`（无书城 JWT、无 Internal-Token）。

### B3

```text
GET /api/reader/ebooks/{id}/chapters          # 无 content、无 chapterFileId
GET /api/reader/ebooks/{id}/chapters/{no}     # 鉴权后 BFF 拉文本
```

```java
// 伪代码
if (!canReadChapter(user, ebook, chapter)) {
    throw previewDenied(); // 5103，禁止 fetchObjectText
}
String text = liteMediaClient.fetchObjectText(chapter.getChapterFileId());
```

```text
□ 第 3 章 5103，日志无内部 content 请求
□ 第 1～2 章返回正文
□ 目录 JSON 不含 fileId
```

### B4

接口与原文 R2 相同：`/api/reader/notes`、`/progress`。`source_type` 含 `AI_REWRITE`。

划线过长且章节锁定：拒绝保存（防泄文）。

### B5

独立 `studyChatClient`。Tool：`getChapterContent`（走 B3）、`getMyNotes`、`saveNote`、`summarizeChapter`、`rewriteNote`（另存 `AI_REWRITE`）、`mergeNotes`。

会话键：`study:user:{id}:{sid}`。限流每用户每小时 20 次。总结走 `reader_ai_summary_cache`。

锁定章：Tool 返回无权限，Prompt 禁止编造。

### B6

`book_media_ref` + `GET /api/books/{id}/media/{refId}/play`。未解锁 `preview=true`（或按媒资现网参数）；借阅/已购完整播放。细节可对照原文 M2～M3，不必重写视频 Worker。

---

## 七、配置草案

```yaml
bookstore:
  media:
    lite-vod:
      enabled: true
      mock: true
      base-url: http://127.0.0.1:8080
      internal-token: ${BOOKSTORE_MEDIA_TOKEN:}
      connect-timeout-ms: 3000
      read-timeout-ms: 10000
    default-preview-seconds: 300

reader:
  enabled: true
  preview:
    default-chapters: 2
  agent:
    enabled: true
    max-input-chars: 12000
```

联调真实媒资：`mock: false`，Token 与 vod-api 一致。

---

## 八、本板块自测（可全 Mock）

```text
1. 登录 USER
2. sync-chapters 后目录 3 章，第 3 章 locked
3. GET 第 1 章有正文；GET 第 3 章 5103
4. 划线笔记；用户 B 改 A 的笔记失败
5. Agent「总结第 2 章」→ AI_SUMMARY；再「改短」→ AI_REWRITE
6. mock=false 时用媒资板块第八章的 fileId 重复 2～3
```

有视频样例时再跑 B6 试看 / 借阅。

---

## 九、检查表

```text
B0 □ Client + Mock 开关  □ Client 生产路径走 internal（upload/commit/object-url/play-url）+ Token
B1 □ Flyway 无 content_text
B2 □ 同步 TOC  □ ADMIN 鉴权后经 Client 要凭证，前端只 PUT MinIO
B3 □ 5103 不打媒资  □ BFF 读章
B4 □ 笔记隔离  □ 进度
B5 □ 独立 Agent  □ 总结缓存  □ rewrite
B6 □ 绑定 fileId  □ preview 播放
□ 客服 /api/ai/chat 行为不变
```

---

## 十、相关文档

| 文档 | 说明 |
| --- | --- |
| **分步目录** | [线上阅读与学习Agent板块/](./线上阅读与学习Agent板块/00-阅读说明.md)（B0～B6） |
| 联调样例 | [附录-联调样例](./线上阅读与学习Agent板块/附录-联调样例.md) |
| 阅读笔记原文 | [AI阅读笔记模块分步实现指南](./AI阅读笔记模块分步实现指南.md)（保留） |
| 登录 | [登录鉴权实现流程](./登录鉴权实现流程.md) |
| 媒资板块 | [16-媒资平台板块 L0～L5](../../video/docs/16-媒资平台板块/00-阅读说明.md) |
| 绑视频原文 | [15-01](../../video/docs/15-对接智慧书城/01-媒资平台与智慧书城分步实现指南.md)（保留） |
