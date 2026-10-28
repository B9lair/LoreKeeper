package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.lore.ConsistencyReport;
import com.xiaoyu.aiaigent.lore.ConsistencyReviewService;
import com.xiaoyu.aiaigent.lore.Conflict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsistencyCheckToolTest {

    @Test
    void consistencyCheck_withConflicts_formatsEntityAndHistory() {
        ConsistencyReviewService service = mock(ConsistencyReviewService.class);
        long workId = 1L;
        Conflict c = new Conflict("林玄", "惯用手=右手", "惯用手=左手", "?", "3");
        ConsistencyReport report = new ConsistencyReport(
                List.of(c), List.of("她总觉得这事没那么简单"));
        when(service.reviewChapterDryRun(eq(workId), isNull(), anyString())).thenReturn(report);

        ConsistencyCheckTool tool = new ConsistencyCheckTool(service, workId);
        String result = tool.consistencyCheck("第50章主角右手持剑…");

        // workId 在构造时绑定，审查走 dry-run（chapterNo=null）
        verify(service).reviewChapterDryRun(eq(workId), isNull(), eq("第50章主角右手持剑…"));
        assertTrue(result.contains("1 处设定矛盾"));
        assertTrue(result.contains("林玄"));
        assertTrue(result.contains("惯用手=右手"));
        assertTrue(result.contains("惯用手=左手"));
        assertTrue(result.contains("确立于第3章"));
        assertTrue(result.contains("未回收伏笔"));
        assertTrue(result.contains("她总觉得这事没那么简单"));
    }

    @Test
    void consistencyCheck_noConflict_reportsPass() {
        ConsistencyReviewService service = mock(ConsistencyReviewService.class);
        when(service.reviewChapterDryRun(anyLong(), isNull(), anyString()))
                .thenReturn(new ConsistencyReport(List.of(), List.of()));

        ConsistencyCheckTool tool = new ConsistencyCheckTool(service, 7L);
        String result = tool.consistencyCheck("平淡的一章");

        assertTrue(result.contains("一致性检测通过"));
        assertFalse(result.contains("处设定矛盾"));   // 有矛盾时的格式是「发现 N 处设定矛盾」
        assertFalse(result.contains("新章节写"));
        assertFalse(result.contains("未回收伏笔"));
    }

    @Test
    void consistencyCheck_blankText_returnsPromptWithoutReview() {
        ConsistencyReviewService service = mock(ConsistencyReviewService.class);
        ConsistencyCheckTool tool = new ConsistencyCheckTool(service, 7L);

        assertEquals("章节文本为空，无法检测", tool.consistencyCheck("   "));
        verify(service, never()).reviewChapterDryRun(anyLong(), any(), anyString());
    }
}
