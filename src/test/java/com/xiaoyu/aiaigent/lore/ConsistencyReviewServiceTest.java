package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsistencyReviewServiceTest {

    private final EntityExtractor extractor = mock(EntityExtractor.class);

    private final SettingMergeService mergeService = mock(SettingMergeService.class);

    private final ForeshadowRepository foreshadowRepository = mock(ForeshadowRepository.class);

    private final ConsistencyReviewService service =
            new ConsistencyReviewService(extractor, mergeService, foreshadowRepository);

    @Test
    void reviewChapterDryRun_collectsConflictsAndOpenForeshadows() {
        NewSetting conflictSetting = new NewSetting("主角", "惯用手", "右手", "第50章主角右手持剑");
        NewSetting normalSetting = new NewSetting("女二", "身份", "剑修", "第3章女二是剑修");
        ChapterExtraction ex = new ChapterExtraction(
                List.of(conflictSetting, normalSetting), List.of(), List.of(), List.of());

        when(extractor.extract(anyString())).thenReturn(ex);
        when(mergeService.judge(eq(conflictSetting), eq(1L)))
                .thenReturn(new SettingMergeService.JudgeResult(
                        SettingMergeService.MergeResult.CONFLICT, "主角", "惯用手",
                        new WorldSetting(5L, 1L, "person", "主角 的 惯用手：左撇子",
                                "主角", "惯用手", "左撇子", "证据", 3, 1)));
        when(mergeService.judge(eq(normalSetting), eq(1L)))
                .thenReturn(new SettingMergeService.JudgeResult(
                        SettingMergeService.MergeResult.NEW, "女二", "身份", null));
        when(foreshadowRepository.findOpenContentsByWorkId(1L))
                .thenReturn(List.of("她总觉得这事没那么简单"));

        ConsistencyReport report = service.reviewChapterDryRun(1L, 50, "新写的一章");

        assertEquals(1, report.conflicts().size());
        Conflict c = report.conflicts().get(0);
        assertEquals("主角", c.entity());
        assertEquals("惯用手=右手", c.descA());
        assertEquals("惯用手=左撇子", c.descB());
        assertEquals("50", c.chapterA());
        assertEquals("3", c.chapterB());

        assertEquals(List.of("她总觉得这事没那么简单"), report.foreshadowReminders());
        // dry-run 只走 judge 判定，绝不调 merge 落库
        verify(mergeService, never()).merge(any(NewSetting.class), anyLong(), any());
    }

    @Test
    void reviewChapterDryRun_consumesSettingUpdates_asConflicts() {
        SettingUpdate update = new SettingUpdate("主角", "惯用手", "右手", "第50章主角右手持剑");
        ChapterExtraction ex = new ChapterExtraction(
                List.of(), List.of(update), List.of(), List.of());

        when(extractor.extract(anyString())).thenReturn(ex);
        when(mergeService.judge(any(NewSetting.class), eq(1L)))
                .thenReturn(new SettingMergeService.JudgeResult(
                        SettingMergeService.MergeResult.CONFLICT, "主角", "惯用手",
                        new WorldSetting(5L, 1L, "person", "主角 的 惯用手：左撇子",
                                "主角", "惯用手", "左撇子", "证据", 3, 1)));
        when(foreshadowRepository.findOpenContentsByWorkId(1L)).thenReturn(List.of());

        ConsistencyReport report = service.reviewChapterDryRun(1L, 50, "新写的一章");

        assertEquals(1, report.conflicts().size());
        Conflict c = report.conflicts().get(0);
        assertEquals("主角", c.entity());
        assertEquals("惯用手=右手", c.descA());
        assertEquals("惯用手=左撇子", c.descB());
        verify(mergeService, never()).merge(any(NewSetting.class), anyLong(), any());
    }
}
