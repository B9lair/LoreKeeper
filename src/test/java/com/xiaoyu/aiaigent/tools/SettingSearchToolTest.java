package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.HybridSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SettingSearchToolTest {

    @Test
    void settingSearch_usesBoundWorkId_andJoinsTexts() {
        HybridSearchService service = mock(HybridSearchService.class);
        long boundWorkId = 42L;

        Document d1 = Document.builder().id("1").text("青阳城靠灵石阵供暖").build();
        Document d2 = Document.builder().id("2").text("女二柳如烟第一次在青阳城登场").build();
        when(service.search(eq("女二在青阳城的剧情"), eq(boundWorkId), eq(3)))
                .thenReturn(List.of(d1, d2));

        // workId 在构造时绑定，模型只传 query
        SettingSearchTool tool = new SettingSearchTool(service, boundWorkId);
        String result = tool.settingSearch("女二在青阳城的剧情");

        // 检索必须用绑定的 workId（不是模型传参）
        verify(service).search("女二在青阳城的剧情", boundWorkId, 3);
        // 返回值按 --- 拼接、含两条文本
        assertTrue(result.contains("青阳城靠灵石阵供暖"));
        assertTrue(result.contains("女二柳如烟第一次在青阳城登场"));
        assertTrue(result.contains("\n---\n"));
    }

    @Test
    void settingSearch_returnsEmptyOnNoHit() {
        HybridSearchService service = mock(HybridSearchService.class);
        when(service.search(anyString(), anyLong(), eq(3))).thenReturn(List.of());

        SettingSearchTool tool = new SettingSearchTool(service, 7L);
        assertEquals("", tool.settingSearch("不存在的设定"));
    }
}
