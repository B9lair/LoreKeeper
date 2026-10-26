package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingSearchToolTest {

    @Test
    void settingSearch_usesBoundWorkId_andJoinsTexts() {
        HybridSearchService service = mock(HybridSearchService.class);
        long boundWorkId = 42L;

        Document d1 = Document.builder().id("1").text("青阳城靠灵石阵供暖").build();
        Document d2 = Document.builder().id("2").text("女二柳如烟第一次在青阳城登场").build();
        when(service.search(eq("女二在青阳城的剧情"), eq(boundWorkId), eq(3), isNull()))
                .thenReturn(List.of(d1, d2));

        // workId 在构造时绑定，模型只传 query
        SettingSearchTool tool = new SettingSearchTool(service, boundWorkId);
        String result = tool.settingSearch("女二在青阳城的剧情");

        // 检索必须用绑定的 workId（不是模型传参）
        verify(service).search("女二在青阳城的剧情", boundWorkId, 3, null);
        // 返回值按 --- 拼接、含两条文本
        assertTrue(result.contains("青阳城靠灵石阵供暖"));
        assertTrue(result.contains("女二柳如烟第一次在青阳城登场"));
        assertTrue(result.contains("\n---\n"));
    }

    @Test
    void settingSearch_returnsEmptyOnNoHit() {
        HybridSearchService service = mock(HybridSearchService.class);
        when(service.search(anyString(), anyLong(), eq(3), isNull())).thenReturn(List.of());

        SettingSearchTool tool = new SettingSearchTool(service, 7L);
        assertEquals("（未检索到与当前作品相关的设定片段）", tool.settingSearch("不存在的设定"));
    }

    @Test
    void settingSearch_preferredOnly_whenPreferredSufficient() {
        HybridSearchService service = mock(HybridSearchService.class);
        Set<String> preferredTypes = Set.of("setting", "foreshadow");
        Document s1 = Document.builder().id("s1").text("英莲是甄士隐之女").metadata(Map.of("type", "setting")).build();
        Document s2 = Document.builder().id("s2").text("英莲被拐子拐走").metadata(Map.of("type", "setting")).build();
        Document f1 = Document.builder().id("f1").text("伏笔：英莲身世待揭").metadata(Map.of("type", "foreshadow")).build();
        when(service.search(eq("英莲的身世"), eq(7L), eq(3), eq(preferredTypes)))
                .thenReturn(List.of(s1, s2, f1));

        SettingSearchTool tool = new SettingSearchTool(service, 7L, null, preferredTypes);
        String result = tool.settingSearch("英莲的身世");

        // 优先召回已满 topK，不再全量补
        verify(service).search("英莲的身世", 7L, 3, preferredTypes);
        verify(service, never()).search("英莲的身世", 7L, 3, null);
        assertTrue(result.contains("英莲是甄士隐之女"));
        assertTrue(result.contains("英莲被拐子拐走"));
        assertTrue(result.contains("伏笔：英莲身世待揭"));
    }

    @Test
    void settingSearch_backfillsChapter_whenPreferredInsufficient() {
        HybridSearchService service = mock(HybridSearchService.class);
        Set<String> preferredTypes = Set.of("setting", "foreshadow");
        Document s1 = Document.builder().id("s1").text("英莲是甄士隐之女").metadata(Map.of("type", "setting")).build();
        Document f1 = Document.builder().id("f1").text("伏笔：英莲身世待揭").metadata(Map.of("type", "foreshadow")).build();
        // 全量召回里 s1 与 preferred 重复（应去重），c1 为章节补位
        Document c1 = Document.builder().id("c1").text("第1章：甄士隐梦见英莲").metadata(Map.of("type", "chapter", "chapterNo", "1")).build();
        when(service.search(eq("英莲的身世"), eq(7L), eq(3), eq(preferredTypes)))
                .thenReturn(List.of(s1, f1));
        when(service.search(eq("英莲的身世"), eq(7L), eq(3), isNull()))
                .thenReturn(List.of(s1, c1));

        SettingSearchTool tool = new SettingSearchTool(service, 7L, null, preferredTypes);
        String result = tool.settingSearch("英莲的身世");

        int idxSetting = result.indexOf("英莲是甄士隐之女");
        int idxForeshadow = result.indexOf("伏笔：英莲身世待揭");
        int idxChapter = result.indexOf("第1章：甄士隐梦见英莲");
        assertTrue(idxSetting >= 0);
        assertTrue(idxForeshadow >= 0);
        assertTrue(idxChapter >= 0);
        // 类型优先：设定/伏笔在前，章节补位在后
        assertTrue(idxSetting < idxChapter);
        assertTrue(idxForeshadow < idxChapter);
        // 去重：s1 只出现一次（不因全量重复召回而重复）
        assertFalse(result.indexOf("英莲是甄士隐之女", idxSetting + 1) >= 0);
    }
}
