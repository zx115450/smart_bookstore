package com.zx.ai.config;

import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 向量检索配置：仅在 {@code ai.rag.enabled=true} 时启用。
 * <p>
 * 显式 {@link Import} Milvus 自动装配：
 * <ul>
 *   <li>A 阶段为避免无 Milvus 时启动失败，在 {@code application.yaml} 用
 *       {@code spring.autoconfigure.exclude} 屏蔽了 Milvus 自动装配；</li>
 *   <li>开启 RAG 时本类显式 {@code @Import} 该自动装配（显式导入不受 autoconfigure exclude 影响），
 *       前提是本地已启动 Milvus（见 {@code docs/AI模块分板块实施流程.md} G.0.1）。</li>
 * </ul>
 * Embedding 模型由 {@code spring-ai-starter-model-openai} 按 {@code spring.ai.openai.embedding.*}
 * 自动提供（通义 text-embedding-v3，1024 维），Milvus 自动装配会复用该 {@code EmbeddingModel}。
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@Import(MilvusVectorStoreAutoConfiguration.class)
public class AiVectorConfig {
}
