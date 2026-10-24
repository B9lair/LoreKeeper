package com.xiaoyu.aiaigent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfMergerTest {

    @Test
    void merge_dedupsByDocIdAndAccumulatesScore() {
        Document d1 = Document.builder().id("1").text("a").build();
        Document d2 = Document.builder().id("2").text("b").build();
        Document d1again = Document.builder().id("1").text("a-dup").build();

        RrfMerger merger = RrfMerger.withDefaultK();
        List<Document> merged = merger.merge(List.of(d1, d2), List.of(d1again));

        assertEquals(2, merged.size());            // 同 id 去重
        assertEquals("1", merged.get(0).getId());  // id=1 出现在两路，分数最高
    }

    @Test
    void merge_handlesEmptyList() {
        RrfMerger merger = RrfMerger.withDefaultK();
        assertTrue(merger.merge(List.of()).isEmpty());
    }
}