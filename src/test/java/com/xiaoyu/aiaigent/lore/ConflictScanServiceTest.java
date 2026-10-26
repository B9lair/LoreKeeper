package com.xiaoyu.aiaigent.lore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConflictScanServiceTest {

    @Test
    void filterChapterDocs_keepsOnlyChapterType() {
        Document chapter = new Document("章节片段", Map.of("type", "chapter", "chapterNo", 3));
        Document setting = new Document("设定片段", Map.of("type", "setting", "entityId", "5"));

        List<Document> result = ConflictScanService.filterChapterDocs(List.of(chapter, setting));

        assertEquals(1, result.size());
        assertEquals("章节片段", result.get(0).getText());
    }

    @Test
    void filterChapterDocs_nullSafe() {
        assertTrue(ConflictScanService.filterChapterDocs(null).isEmpty());
    }
}
