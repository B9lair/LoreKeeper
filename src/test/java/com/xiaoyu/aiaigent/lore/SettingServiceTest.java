package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SettingServiceTest {

    private final WorldSettingRepository worldSettingRepository = mock(WorldSettingRepository.class);
    private final ChunkingService chunkingService = mock(ChunkingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ConflictScanService conflictScanService = mock(ConflictScanService.class);
    private final CharacterRepository characterRepository = mock(CharacterRepository.class);

    private final SettingService service = new SettingService(
            worldSettingRepository, chunkingService, vectorStore, jdbcTemplate, conflictScanService, characterRepository);

    @Test
    void save_new_createsReindexesAndReturnsConflicts() {
        when(worldSettingRepository.nextVersion(eq(1L), eq("主角"))).thenReturn(1);
        WorldSetting saved = new WorldSetting(
                5L, 1L, "rule", "主角 的 惯用手：右", "主角", "惯用手", "右", null, null, 1);
        when(worldSettingRepository.save(any(WorldSetting.class))).thenReturn(saved);
        when(chunkingService.settingToDocument(anyLong(), anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn(new Document("主角 的 惯用手：右", Map.of()));
        when(conflictScanService.scan(eq(1L), eq("主角"), eq("惯用手"), eq("右")))
                .thenReturn(List.of(new ConflictScanItem("3", "原文", "原因", true)));

        SettingSaveResult r = service.save(1L, new SettingSaveRequest(
                null, "主角", "惯用手", "右", null, null, null, null));

        assertEquals(5L, r.id());
        assertEquals(1, r.version());
        assertEquals(1, r.conflicts().size());
        verify(worldSettingRepository).save(any(WorldSetting.class));
        verify(vectorStore).add(anyList());   // 补设定向量索引（踩坑 1）
    }

    @Test
    void save_update_wrongId_throws404() {
        when(worldSettingRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.save(1L, new SettingSaveRequest(
                        5L, "主角", "惯用手", "右", null, null, null, null)));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void save_blankFields_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.save(1L, new SettingSaveRequest(
                        null, "", "惯用手", "右", null, null, null, null)));
        assertEquals(400, ex.getStatusCode().value());
        verify(worldSettingRepository, never()).save(any(WorldSetting.class));
    }
}
