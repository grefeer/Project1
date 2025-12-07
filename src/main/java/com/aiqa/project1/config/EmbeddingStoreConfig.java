package com.aiqa.project1.config;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingStoreConfig {
    @Bean
    public MilvusEmbeddingStore embeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host("localhost")
                .port(19530)
                .databaseName("Project1")
                .collectionName("test")
                .dimension(1024)
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                // .username("root")
                // .password("Milvus")
                .autoFlushOnInsert(false)
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
    }
}
