package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsistencyReviewServiceTest {

    private final EntityExtractor extractor = mock(EntityExtractor.class);

    private final SettingMergeService mergeService = mock(SettingMergeService.class);

    private final WorldSettingRepository worldSettingRepository = mock(WorldSettingRepository.class);

    private final ForeshadowRepository foreshadowRepository = mock(ForeshadowRepository.class);

    private final PendingConflictRepository pendingConflictRepository = mock(PendingConflictRepository.class);

    private final ConsistencyReviewService service = new ConsistencyReviewService(extractor, mergeService, worldSettingRepository, foreshadowRepository, pendingConflictRepository);

    @Test
    void reviewChapter_collectsConflictsAndOpenForeshadows() {
        NewSetting conflictSetting = new NewSetting("主角", "惯用手", "右手", "第50章主角右手持剑");
        NewSetting normalSetting = new NewSetting("女二", "身份", "剑修", "第3章女二是剑修");
        ChapterExtraction ex = new ChapterExtraction(
                List.of(conflictSetting, normalSetting), List.of(), List.of(), List.of());

        when(extractor.extract(anyString())).thenReturn(ex);
        when(mergeService.merge(eq(conflictSetting), eq(1L), eq(50)))
                .thenReturn(SettingMergeService.MergeResult.CONFLICT);
        when(mergeService.merge(eq(normalSetting), eq(1L), eq(50)))
                .thenReturn(SettingMergeService.MergeResult.NEW);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.of(new WorldSetting(
                        5L, 1L, "person", "主角 的 惯用手：左撇子",
                        "主角", "惯用手", "左撇子", "证据", 3, 1)));
        when(foreshadowRepository.findOpenContentsByWorkId(1L))
                .thenReturn(List.of("她总觉得这事没那么简单"));

        ConsistencyReport report = service.reviewChapter(1L, 50, "新写的一章");

        assertEquals(1, report.conflicts().size());
        Conflict c = report.conflicts().get(0);
        assertEquals("主角", c.entity());
        assertEquals("惯用手=右手", c.descA());
        assertEquals("惯用手=左撇子", c.descB());
        assertEquals("50", c.chapterA());
        assertEquals("3", c.chapterB());

        assertEquals(List.of("她总觉得这事没那么简单"), report.foreshadowReminders());
        verify(mergeService, times(1)).merge(eq(conflictSetting), eq(1L), eq(50));
        // B6：CONFLICT 应落待确认队列（old_value=历史值，new_value=候选新值）
        verify(pendingConflictRepository).save(eq(1L), eq("主角"), eq("惯用手"),
                eq("左撇子"), eq("右手"), eq("第50章主角右手持剑"), eq(50));
    }

    @Test
    void reviewChapter_consumesSettingUpdates_asConflicts() {
        // settingUpdates（对已有设定的更新）也应纳入审查：值变 → CONFLICT → 落待确认队列
        SettingUpdate update = new SettingUpdate("主角", "惯用手", "右手", "第50章主角右手持剑");
        ChapterExtraction ex = new ChapterExtraction(
                List.of(), List.of(update), List.of(), List.of());

        when(extractor.extract(anyString())).thenReturn(ex);
        when(mergeService.merge(any(NewSetting.class), eq(1L), eq(50)))
                .thenReturn(SettingMergeService.MergeResult.CONFLICT);
        when(worldSettingRepository.findByEntityAndAttribute(eq(1L), eq("主角"), eq("惯用手")))
                .thenReturn(Optional.of(new WorldSetting(
                        5L, 1L, "person", "主角 的 惯用手：左撇子",
                        "主角", "惯用手", "左撇子", "证据", 3, 1)));
        when(foreshadowRepository.findOpenContentsByWorkId(1L)).thenReturn(List.of());

        ConsistencyReport report = service.reviewChapter(1L, 50, "新写的一章");

        assertEquals(1, report.conflicts().size());
        Conflict c = report.conflicts().get(0);
        assertEquals("主角", c.entity());
        assertEquals("惯用手=右手", c.descA());
        assertEquals("惯用手=左撇子", c.descB());
        verify(pendingConflictRepository).save(eq(1L), eq("主角"), eq("惯用手"),
                eq("左撇子"), eq("右手"), any(), eq(50));
    }
}
