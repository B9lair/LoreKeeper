package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 一次性数据灌入
 */
@SpringBootTest
class SeedDataFor23Test {

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private ChunkingService chunkingService;
    @Resource(name = "pgVectorVectorStore") private VectorStore pgVectorVectorStore;

    @Test
    void seed() {
        // 1) 取最新作品
        List<Long> workIds = jdbcTemplate.queryForList(
                "SELECT id FROM work ORDER BY id DESC LIMIT 1", Long.class);
        if (workIds.isEmpty()) {
            fail("work 表为空：请先 POST /auth/register 或 /auth/login 创建默认作品后再跑本测试");
        }
        long workId = workIds.get(0);
        String wid = String.valueOf(workId);

        // 2) 幂等清理旧测试数据
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'workId' = ?", wid);

        // 3) 插入角色
        jdbcTemplate.update(
                "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                workId, "女二", "[\"柳如烟\"]", "配角", "剑修", 1, "alive");

        // 4) 灌 3 条设定
        List<Document> settings = List.of(
                chunkingService.settingToDocument(workId, 9001L, 3, "setting", "青阳城靠灵石阵供暖"),
                chunkingService.settingToDocument(workId, 9002L, 1, "setting", "女二柳如烟第一次在青阳城登场"),
                chunkingService.settingToDocument(workId, 9003L, 1, "setting", "主角惯用左手持剑"));
        pgVectorVectorStore.add(settings);

        System.out.println(">>> 已灌入数据到 workId=" + workId + "，可 curl /api/ai/lore/chat 验证");
    }
}
