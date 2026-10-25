package com.xiaoyu.aiaigent.lore;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 抽取闭环验收：真连 LLM，用固定样章验证抽取质量。
 * 注意：LLM 结果每次不同，断言保持宽松（只验证「能抽到 + 证据非空」，不验证具体值）。
 * 建议手动/单独跑，不放常规 CI。
 */
@SpringBootTest
class EntityExtractorIntegrationTest {

    @Resource
    private EntityExtractor extractor;

    /** 固定样章：含 2 条持久设定 + 1 条伏笔 + 剧情/描写反例 */
    private static final String SAMPLE = """
            主角林玄是青阳城人，天生左撇子。
            青阳城靠灵石阵供暖，四季如春。
            她总觉得这事没那么简单。
            他推门走了进去，看见她有一双大眼睛。""";

    @Test
    void extract_shouldReturnSettingsAndForeshadowsWithEvidence() {
        ChapterExtraction ex = extractor.extract(SAMPLE);

        assertNotNull(ex);
        assertFalse(ex.settings().isEmpty(), "应抽到至少一条持久设定（如「左撇子」「灵石阵供暖」）");
        assertFalse(ex.foreshadows().isEmpty(), "应抽到至少一条伏笔（如「她总觉得这事没那么简单」）");
        assertTrue(ex.settings().stream().allMatch(s -> s.evidence() != null && !s.evidence().isBlank()),
                "所有设定必须带非空 evidence（证据锚定）");
        assertTrue(ex.foreshadows().stream().allMatch(f -> f.evidence() != null && !f.evidence().isBlank()),
                "所有伏笔必须带非空 evidence");
    }
}
