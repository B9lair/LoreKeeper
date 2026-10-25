package com.xiaoyu.aiaigent.eval;

import java.util.List;

/**
 * 一致性检测评测器：用混淆矩阵（TP/FP/FN/TN）算 F1 与准确率。
 * <p>
 * 方案 3.4 明确要求「一致性检测用混淆矩阵算 F1，而非"检出==标注"的严格匹配」。
 * 纯函数、无状态，输入是「预测是否冲突」与「标注是否冲突」两个布尔序列。
 */
public class ConsistencyEvaluator {

    /** 混淆矩阵 + 派生指标 */
    public record ConsistencyMetrics(int tp, int fp, int fn, int tn,
                                     double precision, double recall, double f1, double accuracy) {
        @Override
        public String toString() {
            return String.format(
                    "TP=%d FP=%d FN=%d TN=%d | Precision=%.4f Recall=%.4f F1=%.4f Accuracy=%.4f",
                    tp, fp, fn, tn, precision, recall, f1, accuracy);
        }
    }

    /**
     * @param predicted 检测器输出：该样本是否被判为「冲突」
     * @param expected  人工标注：该样本是否真的冲突
     */
    public ConsistencyMetrics evaluate(List<Boolean> predicted, List<Boolean> expected) {
        if (predicted == null || expected == null || predicted.size() != expected.size()) {
            throw new IllegalArgumentException("predicted 与 expected 数量必须一致");
        }

        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < predicted.size(); i++) {
            boolean p = Boolean.TRUE.equals(predicted.get(i)); // 预测为冲突
            boolean e = Boolean.TRUE.equals(expected.get(i));  // 标注为冲突
            if (p && e) {
                tp++;
            } else if (p) {
                fp++;
            } else if (e) {
                fn++;
            } else {
                tn++;
            }
        }

        int n = predicted.size();
        double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
        double f1 = (precision + recall) == 0.0 ? 0.0 : 2 * precision * recall / (precision + recall);
        double accuracy = n == 0 ? 0.0 : (double) (tp + tn) / n;

        return new ConsistencyMetrics(tp, fp, fn, tn, precision, recall, f1, accuracy);
    }
}
