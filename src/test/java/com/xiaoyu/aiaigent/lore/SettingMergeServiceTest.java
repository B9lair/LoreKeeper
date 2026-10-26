package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingMergeServiceTest {

    private final CharacterRepository characterRepository = mock(CharacterRepository.class);
    private final WorldSettingRepository worldSettingRepository = mock(WorldSettingRepository.class);
    private final SettingMergeService service =
            new SettingMergeService(characterRepository, worldSettingRepository,
                    mock(ChunkingService.class), mock(VectorStore.class));

    /** save 返回带 id 的副本，供主类 merge 里 indexSetting(saved.id()) 使用（mock 默认返回 null 会 NPE） */
    @BeforeEach
    void stubSave() {
        when(worldSettingRepository.save(any(WorldSetting.class))).thenAnswer(inv -> {
            WorldSetting ws = inv.getArgument(0);
            return new WorldSetting(99L, ws.workId(), ws.type(), ws.content(), ws.entity(),
                    ws.attribute(), ws.value(), ws.evidence(), ws.establishedChapter(), ws.version());
        });
    }

    @Test
    void merge_newEntity_returnsNEW_andSaves() {
        when(characterRepository.findByNameOrAlias(eq(1L), eq("主角"))).thenReturn(Optional.empty());
        when(worldSettingRepository.existsByEntity(eq(1L), eq("主角"))).thenReturn(false);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.empty());

        NewSetting s = new NewSetting("主角", "惯用手", "左撇子", "第3章主角天生左撇子");
        SettingMergeService.MergeResult r = service.merge(s, 1L, 3);

        assertEquals(SettingMergeService.MergeResult.NEW, r);
        verify(worldSettingRepository).save(any(WorldSetting.class));
    }

    @Test
    void merge_knownEntityNewAttribute_returnsUPDATE() {
        when(characterRepository.findByNameOrAlias(eq(1L), eq("主角")))
                .thenReturn(Optional.of(new CharacterRepository.Character(
                        1L, 1L, "主角", List.of(), null, null, 3, null)));
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("会飞")))
                .thenReturn(Optional.empty());

        NewSetting s = new NewSetting("主角", "会飞", "能御剑", "第10章主角御剑而起");
        SettingMergeService.MergeResult r = service.merge(s, 1L, 10);

        assertEquals(SettingMergeService.MergeResult.UPDATE, r);
        verify(worldSettingRepository).save(any(WorldSetting.class));
    }

    @Test
    void merge_sameAssertion_returnsDUPLICATE() {
        when(characterRepository.findByNameOrAlias(eq(1L), eq("主角"))).thenReturn(Optional.empty());
        when(worldSettingRepository.existsByEntity(eq(1L), eq("主角"))).thenReturn(true);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.of(new WorldSetting(
                        5L, 1L, "person", "主角 的 惯用手：左撇子",
                        "主角", "惯用手", "左撇子", "证据", 3, 1)));

        NewSetting s = new NewSetting("主角", "惯用手", "左撇子", "第3章主角天生左撇子");
        SettingMergeService.MergeResult r = service.merge(s, 1L, 3);

        assertEquals(SettingMergeService.MergeResult.DUPLICATE, r);
        verify(worldSettingRepository, never()).save(any(WorldSetting.class));
    }

    @Test
    void merge_conflictingValue_returnsCONFLICT() {
        when(characterRepository.findByNameOrAlias(eq(1L), eq("主角"))).thenReturn(Optional.empty());
        when(worldSettingRepository.existsByEntity(eq(1L), eq("主角"))).thenReturn(true);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.of(new WorldSetting(
                        5L, 1L, "person", "主角 的 惯用手：左撇子",
                        "主角", "惯用手", "左撇子", "证据", 3, 1)));

        NewSetting s = new NewSetting("主角", "惯用手", "右手", "第50章主角右手持剑");
        SettingMergeService.MergeResult r = service.merge(s, 1L, 50);

        assertEquals(SettingMergeService.MergeResult.CONFLICT, r);
        verify(worldSettingRepository, never()).save(any(WorldSetting.class));
    }

    @Test
    void merge_aliasEntity_normalizesToCanonicalName() {
        // entity 用别名「林玄」，character 标准名「主角」→ 落库应归一为「主角」
        when(characterRepository.findByNameOrAlias(eq(1L), eq("林玄")))
                .thenReturn(Optional.of(new CharacterRepository.Character(
                        1L, 1L, "主角", List.of("林玄"), null, null, 3, null)));
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("出身")))
                .thenReturn(Optional.empty());
        when(worldSettingRepository.nextVersion(eq(1L), eq("主角"))).thenReturn(2);

        NewSetting s = new NewSetting("林玄", "出身", "青阳城林家旁支", "第8章主角林玄出身青阳城林家旁支");
        SettingMergeService.MergeResult r = service.merge(s, 1L, 8);

        assertEquals(SettingMergeService.MergeResult.UPDATE, r);
        ArgumentCaptor<WorldSetting> captor = ArgumentCaptor.forClass(WorldSetting.class);
        verify(worldSettingRepository).save(captor.capture());
        assertEquals("主角", captor.getValue().entity());
        assertEquals("出身", captor.getValue().attribute());
    }

    @Test
    void mergeUpdate_valueChanged_returnsCONFLICT() {
        when(characterRepository.findByNameOrAlias(eq(1L), eq("主角"))).thenReturn(Optional.empty());
        when(worldSettingRepository.existsByEntity(eq(1L), eq("主角"))).thenReturn(true);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.of(new WorldSetting(
                        5L, 1L, "person", "主角 的 惯用手：左撇子",
                        "主角", "惯用手", "左撇子", "证据", 3, 1)));

        SettingUpdate u = new SettingUpdate("主角", "惯用手", "右手", "第50章主角右手持剑");
        SettingMergeService.MergeResult r = service.mergeUpdate(u, 1L, 50);

        assertEquals(SettingMergeService.MergeResult.CONFLICT, r);
        verify(worldSettingRepository, never()).save(any(WorldSetting.class));
    }

    @Test
    void mergeUpdate_null_returnsDUPLICATE() {
        assertEquals(SettingMergeService.MergeResult.DUPLICATE, service.mergeUpdate(null, 1L, 1));
    }
}
