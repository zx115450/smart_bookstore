# B3：试看 BFF 读章

> **阶段**：B3 · **周期**：1～2 天  
> **目标**：用户拉目录与单章正文；锁定章返回 5103 且**绝不**调用媒资拉文本。  
> **上一篇**：[B2 导入同步 TOC](./B2-导入同步TOC.md)　**下一篇**：[B4 进度与笔记](./B4-进度与笔记.md)

---

## 本步要做什么

试看策略在书城判定。无权限时只返回业务码，不签发 object-url、不 wget MinIO。

---

## 接口

```text
GET /api/reader/ebooks/{ebookId}/chapters
  # 返回 chapterNo、title、wordCount、locked（或 isPreviewFree）
  # 禁止下发 chapterFileId / sourceFileId / 预签名 URL

GET /api/reader/ebooks/{ebookId}/chapters/{chapterNo}
  # 需登录；鉴权通过后 BFF 拉正文
  # 响应：title、chapterNo、content（纯文本/Markdown）
```

权限伪代码：

```java
if (!canReadChapter(user, ebook, chapter)) {
    throw previewDenied(); // 5103，禁止 fetchObjectText
}
String text = liteMediaClient.fetchObjectText(chapter.getChapterFileId());
// fetchObjectText = internal object-url + HttpClient GET MinIO
```

`canReadChapter` 规则（MVP）：

| 条件 | 结果 |
| --- | --- |
| `is_preview_free=1` | 可读 |
| 用户对该实体书借阅中 / 已购（若绑了 `book_id`） | 可读 |
| 其他 | 5103 |

---

## 错误语义

| 码 | 场景 |
| --- | --- |
| 5101 | 电子书不存在 / 下架 |
| 5103 | 试看拒绝（日志中无 internal object-url） |
| 6002 | 媒资超时 / 5xx |
| 6003 | 章 fileId 在媒资不存在或父未切完 |

不要把媒资 HTTP 状态原样透出给前端。

---

## 验证

```text
1. USER 登录
2. GET 目录：3 章，第 3 章 locked=true；JSON 无 fileId
3. GET 第 1、2 章：200 + 正文
4. GET 第 3 章：5103；抓包 / 日志确认未请求媒资 object-url
5. mock=false：用真实 CHAPTER fileId 重复 2～4
```

---

## 完成标准

- [ ] 目录接口无敏感 fileId / URL
- [ ] 免费章 BFF 返回正文
- [ ] 锁定章 5103，且不调用 `fetchObjectText`
- [ ] 媒资故障映射 6002 / 6003
- [ ] 借阅解锁路径可先 stub，B6 / 借阅模块联调时补全

---

## 本步不做

- 笔记与进度（B4）
- Agent（B5）
- 视频播放（B6）
