package com.xiaoyu.aiaigent.repository;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B1 Repository 集成测试：真连 PostgreSQL，验证 CRUD + work_id 隔离。
 */
@SpringBootTest
class RepositoryIntegrationTest {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private UserRepository userRepository;
    @Resource
    private WorkRepository workRepository;
    @Resource
    private ChapterRepository chapterRepository;
    @Resource
    private WorldSettingRepository worldSettingRepository;
    @Resource
    private ForeshadowRepository foreshadowRepository;

    private long userId;
    private long workId;
    private long otherWorkId;

    @BeforeEach
    void setUp() {
        // 唯一邮箱，避免重复运行冲突
        userId = userRepository.save("b1test_" + System.currentTimeMillis() + "@example.com", "hash");
        workId = workRepository.create(userId, "测试作品", "玄幻");
        otherWorkId = workRepository.create(userId, "另一作品", null);
    }

    @AfterEach
    void tearDown() {
        // 按子表 → 父表顺序清理，避免外键约束
        jdbcTemplate.update("DELETE FROM chapter WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM character WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM work WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void workCrud() {
        List<WorkRepository.Work> works = workRepository.findByUserId(userId);
        assertTrue(works.stream().anyMatch(w -> w.id() == workId), "findByUserId 应包含新建作品");

        Optional<WorkRepository.Work> w = workRepository.findById(workId);
        assertTrue(w.isPresent());
        assertEquals("测试作品", w.get().title());
        assertEquals("玄幻", w.get().genre());
    }

    @Test
    void chapterCrud() {
        long id = chapterRepository.save(workId, 1, "第一章", "draft", "正文内容");
        assertTrue(id > 0, "save 应返回自增 id");

        assertEquals(2, chapterRepository.nextSeq(workId), "nextSeq 应为 MAX(seq)+1");
        assertTrue(chapterRepository.findById(id).isPresent());
        assertEquals(1, chapterRepository.findByWorkId(workId).size());
    }

    @Test
    void worldSettingCrud_withWorkIdIsolation() {
        WorldSettingRepository.WorldSetting ws = WorldSettingRepository.WorldSetting.of(
                workId, "person", "左撇子", "主角", "惯用手", "左", "证据", 1, 1);
        WorldSettingRepository.WorldSetting saved = worldSettingRepository.save(ws);
        assertEquals(1, worldSettingRepository.findByWorkId(workId).size());

        // update
        WorldSettingRepository.WorldSetting updated = new WorldSettingRepository.WorldSetting(
                saved.id(), workId, saved.type(), "新内容", saved.entity(),
                saved.attribute(), "右", saved.evidence(), saved.establishedChapter(), 2);
        worldSettingRepository.update(updated);
        assertEquals("右", worldSettingRepository.findById(saved.id()).orElseThrow().value());

        // deleteById 带 workId：错误 workId 删不掉，正确 workId 删得掉
        assertEquals(0, worldSettingRepository.deleteById(saved.id(), otherWorkId), "跨作品不应删除成功");
        assertEquals(1, worldSettingRepository.deleteById(saved.id(), workId));
        assertEquals(0, worldSettingRepository.findByWorkId(workId).size());
    }

    @Test
    void foreshadowCrud_withWorkIdIsolation() {
        long id = foreshadowRepository.save(workId, "伏笔内容", 3);
        assertTrue(id > 0);
        assertEquals(1, foreshadowRepository.findByWorkId(workId).size());

        // 错误 workId 回收失败
        assertEquals(0, foreshadowRepository.markResolved(id, otherWorkId, 10), "跨作品不应回收成功");

        // 正确 workId 回收成功
        assertEquals(1, foreshadowRepository.markResolved(id, workId, 10));
        ForeshadowRepository.Foreshadow f = foreshadowRepository.findById(id).orElseThrow();
        assertEquals("closed", f.status());
        assertEquals(10, f.resolvedChapter());
    }
}
