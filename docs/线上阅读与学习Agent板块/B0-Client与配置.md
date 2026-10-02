# B0：Client 与配置

> **阶段**：B0 · **周期**：0.5～1 天  
> **目标**：落地 `LiteMediaClient`、配置项与 `com.zx.reader` 空包；Mock 可启，生产路径走 internal + Token。  
> **上一篇**：[阅读说明](./00-阅读说明.md)　**下一篇**：[B1 Flyway 目录表](./B1-Flyway目录表.md)

---

## 本步要做什么

书城不直连媒资库。所有媒资交互经 Client；`mock=true` 时不访问 `8080`。

---

## 包结构

```text
com.zx.media.client
  LiteMediaClient          # 接口
  LiteMediaClientImpl      # RestClient / WebClient
  MockLiteMediaClient      # 内存三章样例
  LiteMediaProperties      # enabled / mock / base-url / internal-token / 超时

com.zx.reader
  ReaderException          # 业务码 51xx / 60xx
  （其余包可先空）
```

---

## 配置

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
```

与媒资对齐：

```text
BOOKSTORE_MEDIA_TOKEN  ===  VOD_INTERNAL_TOKEN
```

`enabled=false`：应用可启动，阅读接口返回「媒资未启用」，不影响借阅 / 客服。

---

## Client 方法（本步先定义）

| 方法 | 生产调用 | Mock 行为 |
| --- | --- | --- |
| `createUploadSignature(assetType)` | `GET /internal/medias/upload-signature` + Token | 返回假 uploadUrl / fileId=`mock-doc-1` |
| `commit(...)` | `POST /internal/medias` + Token | 空成功 |
| `listChapters(sourceFileId)` | `GET /vod/medias/{id}/chapters` | 固定 3 章 mock-c-1..3 |
| `getMedia(fileId)` | `GET /vod/medias/{id}` | FINISHED DOCUMENT |
| `fetchObjectText(fileId)` | object-url + **再 GET MinIO** | 返回测试资源内写死正文 |
| `getPlaySignature(fileId, preview)` | `GET /vod/signature/play` | 返回假 playUrl（B6 再用） |

请求头：`X-Internal-Token: {internal-token}`（仅 internal 路径）。  
超时：connect 3s、read 10s。媒资 5xx / 超时 → 映射书城 `6002`。

---

## Mock 约定

| 项 | 值 |
| --- | --- |
| sourceFileId | `mock-doc-1` |
| 章 fileId | `mock-c-1`、`mock-c-2`、`mock-c-3` |
| 正文 | `classpath:reader/mock/chapter-*.md`（各不少于一段） |

---

## 验证

```text
1. mock=true 启动书城成功
2. 单元测试：MockClient.listChapters 返回 3 章
3. mock=false 且 Token 错：调 upload-signature → 401 映射为业务失败（勿裸抛堆栈给前端）
4. enabled=false：启动成功，阅读相关接口明确提示关闭
```

---

## 完成标准

- [ ] `LiteMediaClient` 接口 + Impl + Mock 可切换
- [ ] 生产路径默认打 `/internal/medias/**`（upload / commit / object-url）并带 Token
- [ ] 配置项可从 `application.yaml` / env 读取
- [ ] `ReaderException` 预留 5101 / 5103 / 6002 / 6003
- [ ] 客服 `/api/ai/chat` 仍可调用（本步未改 AI）

---

## 本步不做

- Flyway 表（B1）
- Controller 读章接口（B3）
- 真实联调 multipart（可选后续；整对象 PUT 足够 md）
