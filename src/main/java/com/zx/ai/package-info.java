/**
 * 智慧书城 AI 客服模块。
 * <p>
 * 职责切分：业务事实走 Tool（查书 / FAQ / 借阅 / 推荐），大模型负责理解意图与组织话术；
 * 可选 RAG（Milvus）仅做语义召回，库存与架位仍回查业务库。
 * <ul>
 *   <li>{@code config} — ChatClient、向量库、业务配置</li>
 *   <li>{@code controller} — HTTP 入口（对话 / 管理端重建索引）</li>
 *   <li>{@code service} — 会话编排与鉴权上下文注入</li>
 *   <li>{@code tool} — 暴露给 LLM 的 Function Calling 适配层</li>
 *   <li>{@code memory} — Redis 多轮对话记忆</li>
 *   <li>{@code faq} / {@code rag} / {@code recommend} — 规则库、向量索引、推荐召回</li>
 *   <li>{@code support} — ThreadLocal 用户上下文、前端卡片收集</li>
 * </ul>
 */
package com.zx.ai;
