package com.xiaoyu.aiaigent.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索：向量 + 实体名关键词 → RRF 融合 → Rerank。
 * 两条检索路径都必须强制 workId 过滤。
 */
@Service
public class HybridSearchService {

    private static final int RECALL_N = 20;

    private final VectorStore vectorStore;              // pgVectorVectorStore
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RerankService> rerankService;
    private final RrfMerger rrfMerger = RrfMerger.withDefaultK();

    public HybridSearchService(VectorStore pgVectorVectorStore,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               ObjectProvider<RerankService> rerankService) {
        this.vectorStore = pgVectorVectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rerankService = rerankService;
    }

    public List<Document> search(String query, long workId, int topK) {
        List<Document> vectorDocs = vectorSearch(query, workId);
        List<Document> entityDocs = entityMatch(query, workId);
        List<Document> fused = rrfMerger.merge(vectorDocs, entityDocs);
        RerankService rerank = rerankService.getIfAvailable();
        if (rerank != null) {
            fused = rerank.rerank(query, fused);
        }
        return fused.stream().limit(topK).toList();
    }

    private List<Document> vectorSearch(String query, long workId) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(RECALL_N)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("workId", String.valueOf(workId))   // 强制 workId 过滤
                        .build())
                .build());
    }

    /** 实体名精确命中提权：从 character 取 name+别名，对 content 做 ILIKE（4.1 L3） */
    private List<Document> entityMatch(String query, long workId) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM character WHERE work_id = ?", String.class, workId);
        List<String> aliases = jdbcTemplate.queryForList(
                "SELECT jsonb_array_elements_text(aliases) FROM character " +
                "WHERE work_id = ? AND aliases IS NOT NULL", String.class, workId);

        Set<String> terms = new HashSet<>();
        terms.addAll(names);
        terms.addAll(aliases);
        terms.removeIf(t -> t == null || t.isBlank() || !query.contains(t));
        if (terms.isEmpty()) {
            return List.of();
        }

        String[] patterns = terms.stream().map(n -> "%" + n + "%").toArray(String[]::new);
        // 关键词路同样强制 workId 过滤
        String sql = "SELECT id, content, metadata FROM vector_store " +
                     "WHERE metadata ->> 'workId' = ? AND content ILIKE ANY (?)";
        return jdbcTemplate.query(sql,
                (rs, i) -> toDocument(rs.getString("id"),
                                      rs.getString("content"),
                                      rs.getString("metadata")),
                String.valueOf(workId), (Object) patterns);
    }

    /** jsonb metadata 用 getString + Jackson 反序列化，并设置 Document.id */
    private Document toDocument(String id, String content, String metadataJson) {
        return Document.builder().id(id).text(content).metadata(parseMetadata(metadataJson)).build();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}