/**
 * LLM Tool Calling 适配层：{@code @Tool} 方法供模型选择调用，内部委托现有业务 Service。
 * <p>
 * 约定：返回 JSON 友好结构（含 found/message）；身份类参数从 {@link com.zx.ai.support.AiUserContext} 读取，不暴露给模型。
 */
package com.zx.ai.tool;
