package com.xiaoyu.aiaigent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HybridSearchServiceIntegrationTest {

    @Resource private HybridSearchService hybridSearchService;
    @Resource private ChunkingService chunkingService;
    @Resource private VectorStore pgVectorVectorStore;
    @Resource private JdbcTemplate jdbcTemplate;

    private static final long TEST_USER_ID = 999001L;
    private static final long TEST_WORK_ID = 999002L;

    @Test
    void search_hitsBothSemanticAndEntityName() {
        // 先清残留
        cleanup();

        try {
            //    按外键顺序准备数据：users → work → character
            //    character.work_id 外键 → work.id；work.user_id 外键 → users.id
            jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                    TEST_USER_ID, "it-test@example.com", "x");
            jdbcTemplate.update(
                    "INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                    TEST_WORK_ID, TEST_USER_ID, "集成测试作品");
            jdbcTemplate.update(
                    "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                    "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                    TEST_WORK_ID, "女二", "[\"柳如烟\"]", "配角", "剑修", 1, "alive");

            // 1) 准备向量种子：语义相关（不含实体名）+ 实体名精确命中
            pgVectorVectorStore.add(List.of(
                    chunkingService.settingToDocument(TEST_WORK_ID, 9001L, 1, "setting", "青阳城靠灵石阵供暖"),
                    chunkingService.settingToDocument(TEST_WORK_ID, 9002L, 1, "setting", "女二柳如烟第一次在青阳城登场"),
                    chunkingService.settingToDocument(TEST_WORK_ID, 9003L, 1, "setting", "主角惯用左手持剑")));

            // 2) 检索
            List<Document> result = hybridSearchService.search("女二在青阳城的剧情", TEST_WORK_ID, 5);

            // 3) 断言：双命中 + 隔离
            assertFalse(result.isEmpty(), "检索结果不应为空");
            assertTrue(result.stream().anyMatch(d -> d.getText().contains("灵石阵")), "应命中语义相关片段");
            assertTrue(result.stream().anyMatch(d -> d.getText().contains("女二")), "应命中实体名精确匹配片段");
            assertTrue(result.stream().allMatch(d -> String.valueOf(TEST_WORK_ID).equals(d.getMetadata().get("workId"))),
                    "所有结果 workId 应正确（隔离生效）");
        } finally {
            cleanup();
        }
    }

    /** 清理测试数据（按外键逆序：character → vector_store → work → users） */
    private void cleanup() {
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'workId' = ?", String.valueOf(TEST_WORK_ID));
        jdbcTemplate.update("DELETE FROM work WHERE id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }
}
