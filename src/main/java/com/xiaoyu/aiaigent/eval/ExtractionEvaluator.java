package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.lore.ChapterExtraction;
import com.xiaoyu.aiaigent.lore.NewSetting;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体抽取评测器：算实体识别 F1 + 伏笔数量指标。
 * <p>
 * 实体集合来自抽取结果的 settings[].entity（去重）；伏笔数量来自 foreshadows.size()。
 * 纯函数、无状态（对应方案 2.6.6「评测集：人工标注 golden set，每次改 prompt 看 F1 变化」）。
 */
public class ExtractionEvaluator {

    /** 一个抽取样本：原文 + 期望实体集合 + 期望伏笔数量 */
    public record ExtractionSample(String text, Set<String> expectedEntities, int expectedForeshadowCount) {
    }

    /** 抽取评测结果 */
    public record ExtractionMetrics(double entityPrecision, double entityRecall, double entityF1,
                                    double foreshadowAccuracy, double foreshadowMae) {
        @Override
        public String toString() {
            return String.format(
                    "Entity P=%.4f R=%.4f F1=%.4f | Foreshadow Acc=%.4f MAE=%.4f",
                    entityPrecision, entityRecall, entityF1, foreshadowAccuracy, foreshadowMae);
        }
    }

    /** 从抽取结果提取「去重后的实体名集合」，空串/空值过滤（与 SettingMergeService.normalize 对齐）。 */
    public static Set<String> entitiesOf(ChapterExtraction ex) {
        if (ex == null || ex.settings() == null) {
            return Set.of();
        }
        Set<String> entities = new HashSet<>();
        for (NewSetting s : ex.settings()) {
            if (s != null && s.entity() != null && !s.entity().isBlank()) {
                entities.add(s.entity().trim());
            }
        }
        return entities;
    }

    /**
     * 别名归一：把实体集合里的别名替换成标准名（如「林玄」→「主角」）。
     * 别名映射由调用方提供（如从 character 表 / 评测种子构建），保持评测器无状态。
     */
    public static Set<String> normalizeEntities(Set<String> entities, Map<String, String> aliasToCanonical) {
        if (entities == null || entities.isEmpty()) {
            return Set.of();
        }
        if (aliasToCanonical == null || aliasToCanonical.isEmpty()) {
            return new HashSet<>(entities);
        }
        Set<String> normalized = new HashSet<>();
        for (String e : entities) {
            normalized.add(aliasToCanonical.getOrDefault(e, e));
        }
        return normalized;
    }

    /**
     * 无别名归一的评测（向后兼容，等价于传空别名映射）。
     */
    public ExtractionMetrics evaluate(List<ExtractionSample> samples, List<ChapterExtraction> extractions) {
        return evaluate(samples, extractions, Map.of());
    }

    /**
     * @param samples           评测样本（与 extractions 按下标一一对应）
     * @param extractions       每个样本的 LLM 抽取结果
     * @param aliasToCanonical  别名 → 标准名 映射（匹配前先对实际实体做归一，如「林玄」→「主角」）
     */
    public ExtractionMetrics evaluate(List<ExtractionSample> samples, List<ChapterExtraction> extractions,
                                      Map<String, String> aliasToCanonical) {
        if (samples == null || samples.isEmpty()) {
            return new ExtractionMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        if (extractions == null || extractions.size() != samples.size()) {
            throw new IllegalArgumentException("extractions 数量必须与 samples 一致");
        }

        double pSum = 0.0, rSum = 0.0;
        double foreshadowHit = 0.0, foreshadowAbsErr = 0.0;

        for (int i = 0; i < samples.size(); i++) {
            ExtractionSample sample = samples.get(i);
            Set<String> expected = sample.expectedEntities() == null ? Set.of() : sample.expectedEntities();
            Set<String> actual = normalizeEntities(entitiesOf(extractions.get(i)), aliasToCanonical);

            int tp = (int) actual.stream().filter(expected::contains).count();
            double precision = actual.isEmpty() ? 0.0 : (double) tp / actual.size();
            double recall = expected.isEmpty() ? 0.0 : (double) tp / expected.size();
            pSum += precision;
            rSum += recall;

            int actualForeshadow = (extractions.get(i) == null || extractions.get(i).foreshadows() == null)
                    ? 0 : extractions.get(i).foreshadows().size();
            int expectedForeshadow = sample.expectedForeshadowCount();
            foreshadowAbsErr += Math.abs(actualForeshadow - expectedForeshadow);
            if (actualForeshadow == expectedForeshadow) {
                foreshadowHit++;
            }
        }

        int n = samples.size();
        double p = pSum / n;
        double r = rSum / n;
        double f1 = (p + r) == 0.0 ? 0.0 : 2 * p * r / (p + r);
        double foreshadowAcc = foreshadowHit / n;
        double foreshadowMae = foreshadowAbsErr / n;

        return new ExtractionMetrics(p, r, f1, foreshadowAcc, foreshadowMae);
    }
}
