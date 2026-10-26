package com.xiaoyu.aiaigent.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.rag.HybridSearchService;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * RAG 可观测接口：分块查看 + 检索测试（供前端「知识库页 / 测试台」使用）。
 * workId 从登录态取，不信任请求参数（2.7 隔离）。
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    @Resource
    private HybridSearchService hybridSearchService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public record ChunkView(String id, String type, String chapterNo, String entityId,
                            String parentId, String content, int length) {
    }

    public record SearchHit(String id, String content, Map<String, Object> metadata) {
    }

    public record SearchRequest(String query, Integer topK) {
    }

    /**
     * 查看当前作品在向量库里的分块。
     * - type 可选：chapter（章节子块）/ setting（设定独立块）/ foreshadow（伏笔块）
     * - chapterId 可选：查某章的子块（type=chapter 且 parentId=chapterId）
     */
    @GetMapping("/chunks")
    public List<ChunkView> chunks(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) Long chapterId) {
        long workId = TenantContext.get().workId();

        StringBuilder sql = new StringBuilder(
                "SELECT id, content, metadata FROM vector_store WHERE metadata ->> 'workId' = ?");
        List<Object> params = new ArrayList<>();
        params.add(String.valueOf(workId));
        if (type != null && !type.isBlank()) {
            sql.append(" AND metadata ->> 'type' = ?");
            params.add(type);
        }
        if (chapterId != null) {
            sql.append(" AND metadata ->> 'parentId' = ?");
            params.add(String.valueOf(chapterId));
        }

        List<ChunkView> list = jdbcTemplate.query(sql.toString(),
                (rs, i) -> {
                    Map<String, Object> meta = parseMetadata(rs.getString("metadata"));
                    String content = rs.getString("content");
                    return new ChunkView(
                            rs.getString("id"),
                            str(meta, "type"),
                            str(meta, "chapterNo"),
                            str(meta, "entityId"),
                            str(meta, "parentId"),
                            content,
                            content == null ? 0 : content.length());
                },
                params.toArray());

        // 按 chapterNo 数值排序（jsonb 里 chapterNo 是数字，SQL 文本序会导致 10 < 2）
        list.sort(Comparator.comparing(
                ChunkView::chapterNo,
                Comparator.nullsLast(Comparator.comparingInt(RagController::parseIntOrMax))));
        return list;
    }

    /** RAG 检索测试：复用混合检索，返回 top-k 命中（含 metadata） */
    @PostMapping("/search")
    public List<SearchHit> search(@RequestBody SearchRequest req) {
        long workId = TenantContext.get().workId();
        int topK = (req.topK() == null || req.topK() <= 0) ? 3 : req.topK();
        List<Document> docs = hybridSearchService.search(req.query(), workId, topK);
        return docs.stream()
                .map(d -> new SearchHit(d.getId(), d.getText(), d.getMetadata()))
                .toList();
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

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int parseIntOrMax(String s) {
        if (s == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
