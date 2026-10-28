package com.xiaoyu.aiaigent.eval;

import java.util.List;

/**
 * 综合评分聚合器（纯函数、无状态，不碰 DB / 向量库 / LLM，可直接单测进 CI）。
 * <p>
 * 对应方案阶段二：
 * <ul>
 *   <li>2.3 加权总分 = 0.35*contextRecall + 0.25*faithfulness + 0.25*answerAccuracy + 0.15*answerRelevancy</li>
 *   <li>2.4 失败样本回放：定位「检索掉分」vs「生成掉分」vs「双重」</li>
 * </ul>
 * 缺失指标（如负样本无 goldenContextIds → recall 无意义、无 reference → accuracy 无意义）约定用 -1 表示，
 * 加权时按剩余权重归一化，保证总分仍落在 [0,1]。
 */
public class ScoreAggregator {

    /** 各指标权重（方案 2.3 固定） */
    public static final double W_RECALL = 0.35;
    public static final double W_FAITHFULNESS = 0.25;
    public static final double W_ACCURACY = 0.25;
    public static final double W_RELEVANCY = 0.15;

    /** judge 分（faithfulness / answerAccuracy）及格线，低于此视为「生成掉分」 */
    public static final double JUDGE_PASS = 0.8;

    /** 加权总分阈值：低于此的样本强制进入失败回放（兜底，与 diagnosis 互补） */
    public static final double FAIL_THRESHOLD = 0.8;

    /** 失败根因分类 */
    public enum FailureKind { NONE, RETRIEVAL, GENERATION, BOTH }

    /** 定位结论：kind + 具体原因描述 */
    public record Diagnosis(FailureKind kind, String reason) {
    }

    /**
     * 单样本 Recall@K：前 K 个检索结果命中期望条目数 / 期望条目总数。
     *
     * @param retrievedIds 检索命中顺序 entityId 列表
     * @param expectedIds  期望命中的设定条目 id（goldenContextIds）
     * @param k            取前 K 个结果
     * @return [0,1]；expectedIds 为空（负样本，无标准答案条目）时返回 -1 表示该指标不适用
     */
    public static double recallAtK(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (expectedIds == null || expectedIds.isEmpty()) {
            return -1.0;
        }
        if (retrievedIds == null || retrievedIds.isEmpty()) {
            return 0.0;
        }
        List<String> topK = retrievedIds.stream().limit(k).toList();
        long hit = topK.stream().filter(expectedIds::contains).count();
        return (double) hit / expectedIds.size();
    }

    /**
     * 加权总分。缺失指标传入 -1（或任意负数），按剩余权重归一化，总分仍落在 [0,1]。
     * 若全部指标缺失，返回 0.0。
     */
    public double composite(double contextRecall, double faithfulness, double answerAccuracy, double answerRelevancy) {
        double[] values = {contextRecall, faithfulness, answerAccuracy, answerRelevancy};
        double[] weights = {W_RECALL, W_FAITHFULNESS, W_ACCURACY, W_RELEVANCY};
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= 0.0) {
                weightedSum += values[i] * weights[i];
                totalWeight += weights[i];
            }
        }
        return totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;
    }

    /**
     * 失败样本根因定位（确定性规则，纯函数）。
     * <ul>
     *   <li>检索掉分：有 goldenContextIds 但 contextRecall &lt; 1.0（证据没召全）</li>
     *   <li>生成掉分：证据已召回，但 faithfulness/answerAccuracy 低于及格线、或存在无据 claim、或幻觉违规</li>
     *   <li>双重：两者同时成立</li>
     * </ul>
     *
     * @param goldenContextIds  期望命中的设定条目 id（空表示负样本，不判检索掉分）
     * @param contextRecall     检索召回率（负样本为 -1）
     * @param faithfulness      归因分
     * @param answerAccuracy    准确分（无 reference 为 -1）
     * @param unsupportedClaims judge 判定的无据 claim
     * @param badCaseViolations judge 判定的幻觉违规
     */
    public Diagnosis diagnose(List<String> goldenContextIds, double contextRecall, double faithfulness,
                              double answerAccuracy, List<String> unsupportedClaims, List<String> badCaseViolations) {
        boolean hasGolden = goldenContextIds != null && !goldenContextIds.isEmpty();
        boolean retrievalFault = hasGolden && contextRecall < 1.0;

        StringBuilder gen = new StringBuilder();
        if (faithfulness < JUDGE_PASS) {
            gen.append(String.format("faithfulness=%.2f ", faithfulness));
        }
        if (answerAccuracy >= 0 && answerAccuracy < JUDGE_PASS) {
            gen.append(String.format("answerAccuracy=%.2f ", answerAccuracy));
        }
        if (unsupportedClaims != null && !unsupportedClaims.isEmpty()) {
            gen.append("存在无据claim ");
        }
        if (badCaseViolations != null && !badCaseViolations.isEmpty()) {
            gen.append("幻觉违规(badCaseViolations) ");
        }
        boolean generationFault = gen.length() > 0;

        if (retrievalFault && generationFault) {
            return new Diagnosis(FailureKind.BOTH,
                    String.format("检索未召回全(recall=%.2f) + 生成掉分: %s", contextRecall, gen.toString().trim()));
        }
        if (retrievalFault) {
            return new Diagnosis(FailureKind.RETRIEVAL,
                    String.format("检索未召回全部 goldenContextIds (recall=%.2f)，证据缺失", contextRecall));
        }
        if (generationFault) {
            return new Diagnosis(FailureKind.GENERATION, "证据已召回，但生成/归因掉分: " + gen.toString().trim());
        }
        return new Diagnosis(FailureKind.NONE, "达标");
    }
}
