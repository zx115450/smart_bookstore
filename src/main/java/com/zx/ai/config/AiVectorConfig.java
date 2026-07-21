package com.zx.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 向量检索配置：仅在 {@code ai.rag.enabled=true} 时启用。
 * <p>
 * 显式 {@link Import} Milvus 自动装配：
 * <ul>
 *   <li>在 {@code application.yaml} 用 {@code spring.autoconfigure.exclude} 屏蔽默认自动装配；</li>
 *   <li>开启 RAG 时本类显式 {@code @Import}（显式导入不受 exclude 影响）。</li>
 * </ul>
 * {@code initialize-schema=false}：Spring AI 2.0 会用空 indexName 调用 describeIndex，
 * 在 Milvus 2.6 上即使已有 {@code embedding} 索引也会报 index not found。集合需事先建好；
 * 本配置在启动时 {@code loadCollection}，避免重启后未加载导致检索失败。
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true")
@Import(MilvusVectorStoreAutoConfiguration.class)
public class AiVectorConfig {

    private static final Logger log = LoggerFactory.getLogger(AiVectorConfig.class);

    @Bean
    ApplicationRunner milvusCollectionLoader(
            ObjectProvider<MilvusServiceClient> milvusClientProvider,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
            @Value("${spring.ai.vectorstore.milvus.collection-name:bookstore_book}") String collectionName
    ) {
        return args -> {
            MilvusServiceClient client = milvusClientProvider.getIfAvailable();
            if (client == null) {
                return;
            }
            R<?> r = client.loadCollection(LoadCollectionParam.newBuilder()
                    .withDatabaseName(databaseName)
                    .withCollectionName(collectionName)
                    .build());
            if (r.getException() != null) {
                log.warn("Milvus loadCollection failed for {}/{}: {}",
                        databaseName, collectionName, r.getException().getMessage());
            } else {
                log.info("Milvus collection loaded: {}/{}", databaseName, collectionName);
            }
        };
    }
}
