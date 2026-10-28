package com.xiaoyu.aiaigent.eval;

import java.util.HashSet;
import java.util.Set;

/**
 * Agent 层评测器（纯函数、无状态，不碰 DB / 向量库 / LLM，可直接单测进 CI）。
 * 对应方案阶段三 3.2：工具调用 F1。
 * <p>
 * 注意：这里只做「确定性比对」。工具序列来自真实链路（依赖 LLM），
 * 由运行器（{@code @Disabled}）采集后喂入本评测器，本评测器本身零 LLM 依赖。
 */
public class AgentEvaluator {

    /** 工具调用 F1 结果：precision / recall / f1（均 0~1） */
    public record ToolMetrics(double precision, double recall, double f1) {
        @Override
        public String toString() {
            return String.format("precision=%.4f, recall=%.4f, f1=%.4f", precision, recall, f1);
        }
    }

    /**
     * 工具调用 F1：期望工具集 vs 实际工具集（去重后比对，忽略调用顺序与重复次数）。
     * TP = 两集合交集；precision = TP/|actual|，recall = TP/|expected|。
     * 双空集视为满分（既没要求调工具、也确实没调）。
     */
    public ToolMetrics toolCallF1(Set<String> expected, Set<String> actual) {
        Set<String> exp = expected == null ? Set.of() : new HashSet<>(expected);
        Set<String> act = actual == null ? Set.of() : new HashSet<>(actual);
        if (exp.isEmpty() && act.isEmpty()) {
            return new ToolMetrics(1.0, 1.0, 1.0);
        }
        long tp = exp.stream().filter(act::contains).count();
        double precision = act.isEmpty() ? 0.0 : (double) tp / act.size();
        double recall = exp.isEmpty() ? 0.0 : (double) tp / exp.size();
        double f1 = (precision + recall == 0.0) ? 0.0 : 2.0 * precision * recall / (precision + recall);
        return new ToolMetrics(precision, recall, f1);
    }
}
