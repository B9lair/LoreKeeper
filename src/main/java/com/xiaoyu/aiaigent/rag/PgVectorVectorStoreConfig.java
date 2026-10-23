package com.xiaoyu.aiaigent.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class PgVectorVectorStoreConfig {

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // DashScope text-embedding 向量维度
                .distanceType(COSINE_DISTANCE)       // 余弦距离
                .indexType(HNSW)                     // HNSW 索引
                // 云 RDS 普通账号对数据库无 CREATE 权限，无法执行 CREATE SCHEMA，故关闭自动建 schema，
                // 表结构由外部手动创建（见下方 DDL）。若账号有 CREATE ON DATABASE 权限可改回 true。
                .initializeSchema(false)
                .schemaName("public")                // 默认 public
                .vectorTableName("vector_store")     // 向量表名
                .maxDocumentBatchSize(10000)         // 单批最大文档数
                .build();
        // 注意：不再启动时 loadDocuments() 灌库，改为「上传章节时增量 add」（见步骤 2.4 / 2.6.7）
    }

    /*
     * 手动建表 DDL（用有 public schema 权限的账号执行一次即可）：
     *
     * CREATE TABLE IF NOT EXISTS public.vector_store (
     *     id uuid PRIMARY KEY,
     *     content text,
     *     metadata jsonb,
     *     embedding vector(1536)
     * );
     * CREATE INDEX IF NOT EXISTS spring_ai_vector_index
     *     ON public.vector_store USING hnsw (embedding vector_cosine_ops);
     */
}
