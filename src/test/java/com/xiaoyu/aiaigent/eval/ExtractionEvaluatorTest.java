package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.lore.ChapterExtraction;
import com.xiaoyu.aiaigent.lore.Foreshadow;
import com.xiaoyu.aiaigent.lore.NewSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionEvaluatorTest {

    private final ExtractionEvaluator evaluator = new ExtractionEvaluator();

    @Test
    void entitiesOf_filtersBlankAndDeduplicates() {
        ChapterExtraction ex = new ChapterExtraction(
                List.of(
                        new NewSetting("主角", "惯用手", "左撇子", "e"),
                        new NewSetting(" 主角 ", "身份", "剑修", "e"),  // 去重
                        new NewSetting("", "身份", "剑修", "e"),        // 空串过滤
                        new NewSetting("青阳城", "供暖", "灵石阵", "e")
                ),
                List.of(), List.of(), List.of(), List.of());

        assertEquals(Set.of("主角", "青阳城"), ExtractionEvaluator.entitiesOf(ex));
    }

    @Test
    void evaluate_fullMatchThenTotalMiss() {
        List<ExtractionEvaluator.ExtractionSample> samples = List.of(
                new ExtractionEvaluator.ExtractionSample("t1", Set.of("主角", "青阳城"), 1),
                new ExtractionEvaluator.ExtractionSample("t2", Set.of("主角"), 1)
        );
        List<ChapterExtraction> extractions = List.of(
                // 样本1：完全命中（实体 2/2，伏笔 1/1）
                new ChapterExtraction(
                        List.of(
                                new NewSetting("主角", "惯用手", "左撇子", "e"),
                                new NewSetting("青阳城", "供暖", "灵石阵", "e")
                        ),
                        List.of(), List.of(new Foreshadow("她总觉得这事没那么简单", "e")), List.of(), List.of()),
                // 样本2：全漏（实体 0，伏笔 0）
                ChapterExtraction.empty()
        );

        ExtractionEvaluator.ExtractionMetrics m = evaluator.evaluate(samples, extractions);

        // 实体：样本1 P=1.0 R=1.0，样本2 P=0 R=0 → 平均 P=0.5 R=0.5 F1=0.5
        assertEquals(0.5, m.entityPrecision(), 1e-9);
        assertEquals(0.5, m.entityRecall(), 1e-9);
        assertEquals(0.5, m.entityF1(), 1e-9);
        // 伏笔：样本1 命中(1==1)，样本2 漏(0!=1) → Acc=0.5，MAE=(0+1)/2=0.5
        assertEquals(0.5, m.foreshadowAccuracy(), 1e-9);
        assertEquals(0.5, m.foreshadowMae(), 1e-9);
    }

    @Test
    void evaluate_emptySamples_returnsZeroMetrics() {
        ExtractionEvaluator.ExtractionMetrics m = evaluator.evaluate(List.of(), List.of());
        assertEquals(0.0, m.entityF1(), 1e-9);
        assertEquals(0.0, m.foreshadowAccuracy(), 1e-9);
    }

    @Test
    void evaluate_sizeMismatch_throws() {
        List<ExtractionEvaluator.ExtractionSample> samples = List.of(
                new ExtractionEvaluator.ExtractionSample("t", Set.of("主角"), 0));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(samples, List.of()));
    }

    @Test
    void normalizeEntities_mapsAliasToCanonical() {
        Map<String, String> aliasMap = Map.of("林玄", "主角", "柳如烟", "女二");
        Set<String> entities = Set.of("林玄", "青阳城", "柳如烟");
        assertEquals(Set.of("主角", "青阳城", "女二"),
                ExtractionEvaluator.normalizeEntities(entities, aliasMap));
    }

    @Test
    void normalizeEntities_emptyAliasMap_returnsCopy() {
        Set<String> entities = Set.of("主角", "青阳城");
        assertEquals(entities, ExtractionEvaluator.normalizeEntities(entities, Map.of()));
    }

    @Test
    void evaluate_withAliasNormalization_hitsAliasAsCanonical() {
        List<ExtractionEvaluator.ExtractionSample> samples = List.of(
                new ExtractionEvaluator.ExtractionSample("t", Set.of("主角"), 0)
        );
        List<ChapterExtraction> extractions = List.of(
                new ChapterExtraction(
                        List.of(new NewSetting("林玄", "惯用手", "左撇子", "e")),
                        List.of(), List.of(), List.of(), List.of())
        );

        // 未归一：林玄 != 主角 → 全 miss
        ExtractionEvaluator.ExtractionMetrics raw = evaluator.evaluate(samples, extractions);
        assertEquals(0.0, raw.entityF1(), 1e-9);

        // 归一后：林玄 → 主角 → 命中
        Map<String, String> aliasMap = Map.of("林玄", "主角");
        ExtractionEvaluator.ExtractionMetrics normalized = evaluator.evaluate(samples, extractions, aliasMap);
        assertEquals(1.0, normalized.entityF1(), 1e-9);
    }
}
