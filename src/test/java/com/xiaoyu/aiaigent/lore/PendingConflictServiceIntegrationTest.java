package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository.PendingConflict;
import com.xiaoyu.aiaigent.repository.UserRepository;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * B6 待确认队列集成测试：真连 PostgreSQL + pgvector。
 * 冲突扫描（LLM）用 mock 隔离，聚焦「accept 采纳新值 + 重建索引 / reject 只改状态」核心链路。
 */
@SpringBootTest
class PendingConflictServiceIntegrationTest {

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private UserRepository userRepository;
    @Resource private WorkRepository workRepository;
    @Resource private WorldSettingRepository worldSettingRepository;
    @Resource private PendingConflictRepository pendingConflictRepository;
    @Resource private PendingConflictService pendingConflictService;

    @MockitoBean
    private ConflictScanService conflictScanService;

    private long userId;
    private long workId;

    @BeforeEach
    void setUp() {
        when(conflictScanService.scan(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());
        userId = userRepository.save("pc_" + System.currentTimeMillis() + "@example.com", "hash");
        workId = workRepository.create(userId, "测试作品", null);
        // 预置历史断言：主角 惯用手=左撇子
        worldSettingRepository.save(WorldSetting.of(
                workId, "person", "主角 的 惯用手：左撇子",
                "主角", "惯用手", "左撇子", "第3章主角天生左撇子", 3, 1));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'workId' = ?", String.valueOf(workId));
        jdbcTemplate.update("DELETE FROM pending_conflict WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM chapter WHERE work_id = ?", workId);
        jdbcTemplate.update("DELETE FROM work WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void accept_adoptsNewValueAndRebuildsIndex() {
        long pid = pendingConflictRepository.save(workId, "主角", "惯用手",
                "左撇子", "右手", "第50章主角右手持剑", 50);

        PendingConflict r = pendingConflictService.accept(workId, pid);

        assertEquals("accepted", r.status());
        // world_setting.value 更新为 new_value
        WorldSetting ws = worldSettingRepository.findByEntityAndAttribute(workId, "主角", "惯用手").orElseThrow();
        assertEquals("右手", ws.value());
        // 向量索引重建（踩坑：采纳新值 = 改设定，必须重建设定索引）
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'type'='setting' AND metadata->>'entityId' = ?",
                Integer.class, String.valueOf(ws.id()));
        assertTrue(cnt != null && cnt > 0, "采纳后应重建设定向量索引");
    }

    @Test
    void reject_onlyUpdatesStatus() {
        long pid = pendingConflictRepository.save(workId, "主角", "惯用手",
                "左撇子", "右手", "第50章主角右手持剑", 50);

        PendingConflict r = pendingConflictService.reject(workId, pid);

        assertEquals("rejected", r.status());
        // world_setting 未被修改
        WorldSetting ws = worldSettingRepository.findByEntityAndAttribute(workId, "主角", "惯用手").orElseThrow();
        assertEquals("左撇子", ws.value());
    }
}
