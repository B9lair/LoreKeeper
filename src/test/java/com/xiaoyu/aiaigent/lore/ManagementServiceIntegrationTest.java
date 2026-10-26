package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.UserRepository;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B3/B4/B5 管理接口集成测试：真连 PostgreSQL + pgvector。
 * 冲突扫描（LLM）用 mock 隔离，聚焦「落库 + work_id 隔离 + 向量索引维护」核心链路。
 */
@SpringBootTest
class ManagementServiceIntegrationTest {

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private UserRepository userRepository;
    @Resource private WorkRepository workRepository;
    @Resource private WorldSettingRepository worldSettingRepository;
    @Resource private ForeshadowRepository foreshadowRepository;
    @Resource private WorkService workService;
    @Resource private SettingService settingService;
    @Resource private ForeshadowService foreshadowService;

    @MockitoBean
    private ConflictScanService conflictScanService;

    private long userId;
    private long workId;
    private long otherUserId;
    private long otherWorkId;

    @BeforeEach
    void setUp() {
        when(conflictScanService.scan(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        userId = userRepository.save("mgmt_" + System.currentTimeMillis() + "@example.com", "hash");
        workId = workRepository.create(userId, "测试作品", "玄幻");
        otherUserId = userRepository.save("mgmt_other_" + System.currentTimeMillis() + "@example.com", "hash");
        otherWorkId = workRepository.create(otherUserId, "他人作品", null);
    }

    @AfterEach
    void tearDown() {
        // 先删向量（按 workId 过滤），再删子表，最后删父表
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'workId' IN (?, ?)",
                String.valueOf(workId), String.valueOf(otherWorkId));
        jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM chapter WHERE work_id IN (?, ?)", workId, otherWorkId);
        jdbcTemplate.update("DELETE FROM work WHERE user_id IN (?, ?)", userId, otherUserId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", userId, otherUserId);
    }

    @Test
    void workSwitch_validatesOwnership() {
        // 自己的作品：切换成功并重签 token
        WorkService.SwitchResult ok = workService.switchWork(userId, workId);
        assertEquals(workId, ok.workId());
        assertNotNull(ok.token());

        // 他人作品：403 越权拦截（踩坑 4）
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> workService.switchWork(userId, otherWorkId));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void settingSaveAndDelete_maintainsVectorIndex() {
        // save 新建（会触发一次真实 embedding）
        SettingSaveResult r = settingService.save(workId,
                new SettingSaveRequest(null, "青阳城", "供暖方式", "灵石阵", null, null, 1, null));
        assertTrue(r.id() > 0);

        // world_setting 落库
        assertEquals(1, worldSettingRepository.findByWorkId(workId).size());

        // vector_store 出现 type=setting 记录（踩坑 1：设定向量索引从零建）
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'type' = 'setting' AND metadata->>'entityId' = ?",
                Integer.class, String.valueOf(r.id()));
        assertTrue(cnt != null && cnt > 0, "设定应已向量化");

        // delete（踩坑 2：按 entityId 直删 vector_store）
        settingService.delete(workId, r.id());
        assertEquals(0, worldSettingRepository.findByWorkId(workId).size());

        Integer cntAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'type' = 'setting' AND metadata->>'entityId' = ?",
                Integer.class, String.valueOf(r.id()));
        assertEquals(0, cntAfter == null ? 0 : cntAfter, "删除后向量索引应清除");
    }

    @Test
    void foreshadowCreateAndResolve() {
        // create（触发一次真实 embedding）
        ForeshadowRepository.Foreshadow f = foreshadowService.create(workId, "她总觉得这事没那么简单", 3);
        assertTrue(f.id() > 0);
        assertEquals("open", f.status());

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'type' = 'foreshadow' AND metadata->>'entityId' = ?",
                Integer.class, String.valueOf(f.id()));
        assertTrue(cnt != null && cnt > 0, "伏笔应已向量化");

        // resolve：open → closed
        ForeshadowRepository.Foreshadow resolved = foreshadowService.resolve(workId, f.id(), 10);
        assertEquals("closed", resolved.status());
        assertEquals(10, resolved.resolvedChapter());
    }
}
