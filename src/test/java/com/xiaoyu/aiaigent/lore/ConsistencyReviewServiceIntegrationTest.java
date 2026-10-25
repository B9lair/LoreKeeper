package com.xiaoyu.aiaigent.lore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 一致性审查集成测试：真连 DB（归并落库 + 伏笔查询），mock LLM 抽取（保证确定性）。
 */
@SpringBootTest
class ConsistencyReviewServiceIntegrationTest {

    @Resource private JdbcTemplate jdbcTemplate;

    @Resource private ObjectMapper objectMapper;

    private static final long TEST_USER_ID = 999101L;

    private static final long TEST_WORK_ID = 999102L;

    private EntityExtractor extractor;

    private WorldSettingRepository worldSettingRepository;

    private ForeshadowRepository foreshadowRepository;

    private CharacterRepository characterRepository;

    private SettingMergeService mergeService;

    private ConsistencyReviewService service;

    @BeforeEach
    void setup() {
        // 真连 DB 的 repository
        worldSettingRepository = new WorldSettingRepository(jdbcTemplate);
        foreshadowRepository = new ForeshadowRepository(jdbcTemplate);
        characterRepository = new CharacterRepository(jdbcTemplate, objectMapper);

        // mock LLM 抽取
        extractor = mock(EntityExtractor.class);

        mergeService = new SettingMergeService(characterRepository, worldSettingRepository);
        service = new ConsistencyReviewService(extractor, mergeService, worldSettingRepository, foreshadowRepository);

        // 准备隔离数据：users → work → character（外键顺序）
        cleanup();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "review-it@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "审查集成测试作品");
        jdbcTemplate.update(
                "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                TEST_WORK_ID, "主角", "[\"林玄\"]", "主角", null, 1, "alive");
        // 预置历史断言 + 一条 open 伏笔
        worldSettingRepository.save(WorldSetting.of(
                TEST_WORK_ID, "person", "主角 的 惯用手：左撇子",
                "主角", "惯用手", "左撇子", "第3章主角天生左撇子", 3, 1));
        jdbcTemplate.update(
                "INSERT INTO foreshadow (work_id, content, planted_chapter, status) VALUES (?, ?, ?, 'open')",
                TEST_WORK_ID, "她总觉得这事没那么简单", 3);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM work WHERE id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }

    @Test
    void reviewChapter_persistsNewSetting_andReportsConflictAndForeshadow() {
        // mock LLM 返回：一条 CONFLICT（主角 惯用手=右手）+ 一条 NEW（女二 身份=剑修）
        NewSetting conflict = new NewSetting("主角", "惯用手", "右手", "第50章主角右手持剑");
        NewSetting newOne = new NewSetting("女二", "身份", "剑修", "第50章女二柳如烟是剑修");
        when(extractor.extract(anyString())).thenReturn(new ChapterExtraction(
                List.of(conflict, newOne), List.of(), List.of(), List.of()));

        ConsistencyReport report = service.reviewChapter(TEST_WORK_ID, 50, "新写的一章");

        // ① 冲突被报告，带两章证据
        assertEquals(1, report.conflicts().size());
        Conflict c = report.conflicts().get(0);
        assertEquals("主角", c.entity());
        assertEquals("惯用手=右手", c.descA());
        assertEquals("惯用手=左撇子", c.descB());
        assertEquals("50", c.chapterA());
        assertEquals("3", c.chapterB());

        // ② NEW 真的落库了
        Optional<WorldSetting> saved = worldSettingRepository
                .findByEntityAndAttribute(TEST_WORK_ID, "女二", "身份");
        assertTrue(saved.isPresent(), "NEW 设定应已写入 world_setting");
        assertEquals("剑修", saved.get().value());

        // ③ 历史断言没被 CONFLICT 覆盖
        Optional<WorldSetting> hist = worldSettingRepository
                .findByEntityAndAttribute(TEST_WORK_ID, "主角", "惯用手");
        assertTrue(hist.isPresent());
        assertEquals("左撇子", hist.get().value(), "CONFLICT 不应覆盖历史断言");

        // ④ open 伏笔被查出
        assertEquals(List.of("她总觉得这事没那么简单"), report.foreshadowReminders());
    }
}
