package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.lore.ChapterExtraction;
import com.xiaoyu.aiaigent.lore.EntityExtractor;
import com.xiaoyu.aiaigent.lore.Foreshadow;
import com.xiaoyu.aiaigent.lore.NewSetting;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 实体抽取评测运行器：真连 LLM，对 golden 样本跑 EntityExtractor，算实体 F1 + 伏笔指标。
 * <p>
 * ⚠️ LLM 结果随机、慢、费 key，故 {@code @Disabled} 默认不进常规 mvn test（与 EntityExtractorIntegrationTest 约定一致）。
 * 手动运行：临时删除 {@code @Disabled} 注解后执行
 * {@code mvn test -Dtest=ExtractionEvalRunnerTest}。
 */
@SpringBootTest
@Disabled("真连 LLM，需手动运行（LLM 结果随机，不放常规 CI）")
@Tag("llm")
class ExtractionEvalRunnerTest {

    @Resource private EntityExtractor extractor;

    @Test
    void evalExtraction() {
        EvalDataset dataset = EvalDatasetLoader.load();
        Map<String, String> aliasToCanonical = buildAliasMap(dataset);

        List<ExtractionEvaluator.ExtractionSample> samples = dataset.extraction().stream()
                .map(e -> new ExtractionEvaluator.ExtractionSample(
                        e.text(), e.expectedEntities(), e.expectedForeshadowCount()))
                .toList();
        List<ChapterExtraction> extractions = dataset.extraction().stream()
                .map(e -> extractor.extract(e.text()))
                .toList();

        // 逐样本明细：定位「实体名对不上」的根因（是抽成具体名、还是漏抽）
        System.out.println("\n========== 实体抽取评测：逐样本明细 ==========");
        for (int i = 0; i < samples.size(); i++) {
            printDetail(i + 1, samples.get(i), extractions.get(i), aliasToCanonical);
        }

        ExtractionEvaluator.ExtractionMetrics metrics =
                new ExtractionEvaluator().evaluate(samples, extractions, aliasToCanonical);
        System.out.println("========== 实体抽取评测汇总（已做别名归一） ==========");
        System.out.println(">>> " + metrics);
        assertFalse(dataset.extraction().isEmpty(), "评测集 extraction 样本不应为空");
    }

    /** 从种子数据的 characters（name + aliases）构建「别名 → 标准名」映射，如 林玄→主角 */
    private Map<String, String> buildAliasMap(EvalDataset dataset) {
        Map<String, String> aliasToCanonical = new HashMap<>();
        if (dataset.seed() != null && dataset.seed().characters() != null) {
            for (EvalDataset.CharacterSeed c : dataset.seed().characters()) {
                if (c.aliases() != null) {
                    for (String alias : c.aliases()) {
                        aliasToCanonical.put(alias, c.name());
                    }
                }
            }
        }
        return aliasToCanonical;
    }

    /** 打印单个样本：期望 vs 实际实体（已归一）、漏抽/多抽、以及 LLM 抽取的完整设定/伏笔明细 */
    private void printDetail(int index, ExtractionEvaluator.ExtractionSample sample, ChapterExtraction ex,
                             Map<String, String> aliasToCanonical) {
        Set<String> expected = sample.expectedEntities();
        Set<String> actual = ExtractionEvaluator.normalizeEntities(
                ExtractionEvaluator.entitiesOf(ex), aliasToCanonical);

        Set<String> missed = new HashSet<>(expected);
        missed.removeAll(actual);
        Set<String> extra = new HashSet<>(actual);
        extra.removeAll(expected);

        int actualForeshadow = (ex == null || ex.foreshadows() == null) ? 0 : ex.foreshadows().size();

        System.out.printf("[样本 %d] %s%n", index, sample.text());
        System.out.printf("  期望实体=%s  伏笔数=%d%n", expected, sample.expectedForeshadowCount());
        System.out.printf("  实际实体=%s  伏笔数=%d%n", actual, actualForeshadow);
        if (!missed.isEmpty()) {
            System.out.printf("  [漏抽实体] %s%n", missed);
        }
        if (!extra.isEmpty()) {
            System.out.printf("  [多抽实体] %s%n", extra);
        }

        if (ex != null && ex.settings() != null && !ex.settings().isEmpty()) {
            System.out.println("  抽取到的设定:");
            for (NewSetting s : ex.settings()) {
                System.out.printf("    - entity=%s, attribute=%s, value=%s (evidence=%s)%n",
                        s.entity(), s.attribute(), s.value(), s.evidence());
            }
        }
        if (ex != null && ex.foreshadows() != null && !ex.foreshadows().isEmpty()) {
            System.out.println("  抽取到的伏笔:");
            for (Foreshadow f : ex.foreshadows()) {
                System.out.printf("    - %s (evidence=%s)%n", f.content(), f.evidence());
            }
        }
        System.out.println();
    }
}
