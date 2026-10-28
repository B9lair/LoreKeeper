package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ChapterRepository;
import com.xiaoyu.aiaigent.repository.UserRepository;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 章节上传入库链路集成测试：真连 PostgreSQL + pgvector。
 * 抽取（LLM）用 mock 隔离，聚焦「存 chapter + 向量化」核心链路。
 */
@SpringBootTest
class ChapterIngestServiceIntegrationTest {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private UserRepository userRepository;
    @Resource
    private WorkRepository workRepository;
    @Resource
    private ChapterRepository chapterRepository;
    @Resource
    private ChapterIngestService chapterIngestService;

    @MockitoBean
    private EntityExtractor entityExtractor;

    private long userId;
    private long workId;

    @BeforeEach
    void setUp() {
        when(entityExtractor.extract(anyString())).thenReturn(ChapterExtraction.empty());
        userId = userRepository.save("b2test_" + System.currentTimeMillis() + "@example.com", "hash");
        workId = workRepository.create(userId, "测试作品", null);
    }

    @AfterEach
    void tearDown() {
        // 先删向量（按 workId 过滤），再删子表，最后删父表
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'workId' = ?", String.valueOf(workId));
        jdbcTemplate.update("DELETE FROM chapter WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM timeline_event WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM work WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void ingestChapter_shouldPersistChapterAndVectorChunks() {
        String text = "主角林玄是青阳城人。\n\n青阳城靠灵石阵供暖，四季如春。\n\n他推门走了进去。";

        ChapterIngestResult result = chapterIngestService.ingestChapter(workId, "第一章", text);

        assertTrue(result.chapterId() > 0, "应返回章节自增 id");
        assertEquals(1, result.seq());

        // chapter 表有记录
        var chapter = chapterRepository.findById(result.chapterId());
        assertTrue(chapter.isPresent());
        assertEquals("第一章", chapter.get().title());
        assertEquals("draft", chapter.get().status());

        // vector_store 有章节子块（按 workId + parentId 过滤）
        Integer vectorCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'workId' = ? AND metadata->>'parentId' = ?",
                Integer.class, String.valueOf(workId), String.valueOf(result.chapterId()));
        assertTrue(vectorCount != null && vectorCount > 0, "向量库应有章节子块");
    }

    @Test
    void ingestChapter_defaultTitleWhenBlank() {
        ChapterIngestResult result = chapterIngestService.ingestChapter(workId, null, "第一段正文。\n\n第二段正文。");
        assertEquals("第 1 章", result.title());
    }
}
