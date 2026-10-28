package com.xiaoyu.aiaigent.lore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityExtractorTest {

    @Test
    void filterEmptyEvidence_dropsNullAndBlankEvidence() {
        ChapterExtraction ex = new ChapterExtraction(
                List.of(
                        new NewSetting("主角", "惯用手", "左撇子", "第3章主角天生左撇子"),
                        new NewSetting("女二", "身份", "剑修", ""),
                        new NewSetting("男二", "身份", "药师", null)
                ),
                List.of(new SettingUpdate("主角", "能力", "会飞", "   ")),
                List.of(
                        new Foreshadow("她总觉得这事没那么简单", "原文原句"),
                        new Foreshadow("另一个伏笔", null)
                ),
                List.of(
                        new TimelineEvent("三年前", "青阳城大战", "原文原句"),
                        new TimelineEvent("昨天", "下雨", "")
                ),
                List.of());

        ChapterExtraction filtered = EntityExtractor.filterEmptyEvidence(ex);

        assertEquals(1, filtered.settings().size());
        assertEquals("主角", filtered.settings().get(0).entity());
        assertTrue(filtered.settingUpdates().isEmpty());
        assertEquals(1, filtered.foreshadows().size());
        assertEquals(1, filtered.timeline().size());
    }

    @Test
    void filterEmptyEvidence_handlesNullExtraction() {
        ChapterExtraction filtered = EntityExtractor.filterEmptyEvidence(null);
        assertNotNull(filtered);
        assertTrue(filtered.settings().isEmpty());
        assertTrue(filtered.foreshadows().isEmpty());
    }
}
