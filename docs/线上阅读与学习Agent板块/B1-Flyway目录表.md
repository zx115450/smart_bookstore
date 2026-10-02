# B1：Flyway 目录表

> **阶段**：B1 · **周期**：0.5 天  
> **目标**：新增线上书相关表；**禁止**章正文列；错误码段避开客服 `5001～5099`。  
> **上一篇**：[B0 Client 与配置](./B0-Client与配置.md)　**下一篇**：[B2 导入同步 TOC](./B2-导入同步TOC.md)

---

## 本步要做什么

按现网 Flyway 版本顺延（当前若为 `V1`，本步用 `V2`）。Entity / Mapper 可同步建，业务接口留给 B2～B4。

完整 DDL 见 [总纲第五节](../线上阅读与学习Agent板块分步实现指南.md#五书城数据模型flyway)。本步必须建：

| 表 | 要点 |
| --- | --- |
| `ebook_book` | `source_file_id` 指向媒资 DOCUMENT |
| `ebook_chapter` | `chapter_file_id`；**无** `content_text` |
| `ebook_reading_progress` | 用户 × 书唯一 |
| `user_note` | `source_type` 含 MANUAL / HIGHLIGHT / AI_* |
| `reader_ai_summary_cache` | 总结缓存（B5） |
| `book_media_ref` | 图书配套视频（B6，可本步先建表） |

---

## 硬性约束

```text
□ ebook_chapter 禁止 content_text / content / body 等正文列
□ chapter_file_id、source_file_id 均为 VARCHAR，不跨库 FK 到媒资
□ 错误码：5101 书不存在 · 5103 试看拒绝 · 5201 笔记无权 · 5301 Agent 限流 · 6002 媒资不可用 · 6003 章未就绪
```

---

## 验证

```bash
# 启动后看 Flyway 历史
# 或 DESC 确认无正文列
DESC ebook_chapter;
```

期望列含：`ebook_id`、`chapter_no`、`title`、`chapter_file_id`、`word_count`、`is_preview_free`。  
**不应出现** `content_text`。

---

## 完成标准

- [ ] 迁移脚本执行成功，应用启动无 Flyway 报错
- [ ] `ebook_chapter` 无正文列
- [ ] Entity 与表字段对齐；Repository 可空查询
- [ ] 与总纲 SQL 一致（含 `book_media_ref` 若本步一并建）

---

## 本步不做

- 管理端导入接口（B2）
- 读章 BFF（B3）
- Agent（B5）
