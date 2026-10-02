# B2：导入同步 TOC

> **阶段**：B2 · **周期**：1～2 天  
> **目标**：ADMIN 创建线上书并同步章目录；真实路径经 Client 走 internal 上传 / commit；切章完成后**媒资回调**书城写 TOC；Mock 可灌三章。  
> **上一篇**：[B1 Flyway](./B1-Flyway目录表.md)　**下一篇**：[B3 试看 BFF 读章](./B3-试看BFF读章.md)

---

## 本步要做什么

管理端导入电子书：**鉴权在书城 → Client 向媒资要凭证 → 浏览器直传 MinIO → commit →（异步切章）媒资 Webhook 回调 → 书城写 `ebook_chapter`**。

前 `reader.preview.default-chapters`（默认 2）章设 `is_preview_free=1`。

**不再轮询** `listChapters` 作为主路径；管理端可保留 `sync-chapters` 作补救（回调丢失 / Mock）。

---

## 接口（建议）

```text
POST /api/reader/admin/ebooks
  # body: title, author, bookId?, format, previewChapters?

POST /api/reader/admin/ebooks/{id}/upload-signature
  # 返回 fileId + uploadUrl（来自 LiteMediaClient，assetType=DOCUMENT）

POST /api/reader/admin/ebooks/{id}/commit
  # body: fileId, filename, splitRule=MARKDOWN
  # → Client.commit → 写 ebook_book.source_file_id
  # → 切章在媒资异步执行，完成后回调书城

POST /api/reader/admin/ebooks/{id}/sync-chapters
  # 补救：Client.listChapters(source_file_id) → 重建 ebook_chapter

POST /api/internal/media/callback
  # 媒资 Worker Webhook（无 JWT，校验 X-Internal-Token）
  # eventType=DOCUMENT_SPLIT + status=PROCESSED → 按 fileId(=source_file_id) 同步 TOC
```

Mock：跳过 upload / commit，`sync-chapters` 或模拟回调直接用 `mock-doc-1` 三章 TOC。

---

## 回调约定

Worker（`DocumentSplitConsumer`）切章终态后 POST `VOD_CALLBACK_URL`：

```json
{
  "fileId": "<sourceFileId>",
  "status": "PROCESSED",
  "eventType": "DOCUMENT_SPLIT",
  "coverUrl": null,
  "duration": null,
  "errorMsg": null
}
```

| 项 | 说明 |
| --- | --- |
| Header | `X-Internal-Token` = `VOD_CALLBACK_AUTH_TOKEN`（建议与 `VOD_INTERNAL_TOKEN` / `BOOKSTORE_MEDIA_TOKEN` 一致） |
| `PROCEDURE` | 视频转码事件，书城忽略 |
| `FAILED` | 打日志，不删已有 TOC |
| HTTP 503 | 书城同步失败时返回，Worker 有限重试 |
| 幂等 | 按 `source_file_id` 先删后插 `ebook_chapter` |

配置示例：

```yaml
# vod-worker
vod.callback.url: http://host.docker.internal:8081/api/internal/media/callback
vod.callback.auth-token: ${VOD_CALLBACK_AUTH_TOKEN:${VOD_INTERNAL_TOKEN:}}

# 书城
bookstore.media.lite-vod.internal-token: ${BOOKSTORE_MEDIA_TOKEN:}
```

---

## 安全要点

```text
1. 管理接口仅 ADMIN
2. 浏览器只拿 uploadUrl 去 PUT MinIO
3. 禁止前端直接调媒资 /vod/signature/upload
4. 回调接口无 JWT，必须校验 Internal-Token；生产建议内网可达
5. 目录接口若对用户开放：响应不得含 chapterFileId / sourceFileId（B3 再收紧）
```

真实联调流程：

```text
ADMIN JWT
  → upload-signature（书城）→ Client → GET /internal/medias/upload-signature
  → 浏览器 PUT MinIO
  → commit（书城）→ Client → POST /internal/medias → 写 source_file_id
  → Worker 切章 FINISHED
  → POST 书城 /api/internal/media/callback（DOCUMENT_SPLIT / PROCESSED）
  → listChapters → 重建 ebook_chapter
```

媒资样例 md：`video/lite-vod/samples/redis-preview.md`（至少 3 个 `##`）。

---

## 验证

```text
1. mock=true：创建 ebook → sync-chapters（或手工 POST callback）→ 库中 3 章，前 2 章 is_preview_free=1
2. mock=false：完整上传闭环，切章后自动出现目录，无需轮询
3. 无 Token / 错 Token 调 callback → 401
4. 非 ADMIN 调管理接口 → 403
5. 同步失败不留下半截脏章：事务回滚或先删后插
```

---

## 完成标准

- [ ] ADMIN 可创建 `ebook_book` 并同步 TOC（回调为主，sync-chapters 可补救）
- [ ] 真实路径：凭证 / commit 经 Client + internal Token
- [ ] 前端只 PUT MinIO，不直打媒资公开上传
- [ ] 前 N 章标记试看免费
- [ ] Mock 与真实两条路径均可演示
- [ ] 媒资 DOCUMENT 切章终态会回调书城

---

## 本步不做

- 用户读章正文（B3）
- 笔记（B4）
- IMAGE 封面绑定（可选，不阻塞）
