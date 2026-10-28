package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 一次性数据灌入：向「隔离的测试作品」灌 3 条设定 + 1 个角色，供 RAG 检索验证。
 *
 * ⚠️ 必须使用固定 TEST_WORK_ID，绝不使用「最新作品」——旧实现 `ORDER BY id DESC LIMIT 1`
 * 拿最新作品后 `DELETE FROM vector_store WHERE metadata->>'workId'=?`，会把用户真实上传章节的
 * 章节/设定/伏笔向量块整体清空（业务表不受影响，导致「知识库分块空、数据管理却有数据」）。
 */
@SpringBootTest
class SeedDataFor23Test {

    private static final long TEST_USER_ID = 998501L;
    private static final long TEST_WORK_ID = 998502L;

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private ChunkingService chunkingService;
    @Resource(name = "pgVectorVectorStore") private VectorStore pgVectorVectorStore;

    @Test
    void seed() {
        // 幂等清理旧测试数据（只动固定 TEST_WORK_ID，绝不碰用户作品）
        cleanup();

        // 1) 建固定测试作品（外键顺序：users → work）
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "eval-seed@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "评测种子作品");

        // 2) 插入角色
        jdbcTemplate.update(
                "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                TEST_WORK_ID, "女二", "[\"柳如烟\"]", "配角", "剑修", 1, "alive");

        // 3) 灌 3 条设定
        List<Document> settings = List.of(
                chunkingService.settingToDocument(TEST_WORK_ID, 9001L, 3, "setting", "青阳城靠灵石阵供暖"),
                chunkingService.settingToDocument(TEST_WORK_ID, 9002L, 1, "setting", "女二柳如烟第一次在青阳城登场"),
                chunkingService.settingToDocument(TEST_WORK_ID, 9003L, 1, "setting", "主角惯用左手持剑"));
        pgVectorVectorStore.add(settings);

        System.out.println(">>> 已灌入数据到测试 workId=" + TEST_WORK_ID);
    }

    @AfterEach
    void teardown() {
        cleanup();
    }

    /** 按外键逆序清理：character → vector_store → work → users（只清固定 TEST_WORK_ID） */
    private void cleanup() {
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'workId' = ?", String.valueOf(TEST_WORK_ID));
        jdbcTemplate.update("DELETE FROM work WHERE id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }
}
