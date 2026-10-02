/**
 * 线上阅读与学习 Agent 板块。
 * <p>
 * B0：{@link com.zx.reader.ReaderException} + {@link com.zx.media.client.LiteMediaClient}。<br>
 * B1：Flyway {@code V2__reader_schema} 目录表（无正文列）+ Entity / Mapper / Repository。<br>
 * B2：媒资切章 Webhook（{@code /api/internal/media/callback}）同步 TOC；管理端导入接口另续。<br>
 * B3：试看目录与读章 BFF（{@code /api/reader/ebooks/{id}/chapters}）；锁定章 5103 且不调媒资。<br>
 * B4：进度（Redis + RabbitMQ 延迟合并落库）与笔记 CRUD；锁定章超长划线拒绝。<br>
 * B5：独立 Study Agent（{@code /api/reader/agent/chat}）；Tool 走 B3 鉴权；总结缓存 / rewrite / merge。<br>
 * B6：{@code book_media_ref} 绑定 VIDEO；{@code /api/books/{id}/media/{refId}/play} 先鉴权再要签名；试看 / 借阅 / 已购三态。<br>
 * 正文不进书城库，经 {@link com.zx.media.client.LiteMediaClient} 向媒资拉文本。
 */
package com.zx.reader;
