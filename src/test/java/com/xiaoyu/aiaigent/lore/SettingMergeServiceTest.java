package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingMergeServiceTest {

    private final CharacterRepository characterRepository = mock(CharacterRepository.class);
    private final WorldSettingRepository worldSettingRepository = mock(WorldSettingRepository.class);
    private final SettingMergeService service =
            new SettingMergeService(characterRepository, worldSettingRepository);

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
}
