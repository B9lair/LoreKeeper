package com.xiaoyu.aiaigent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentEvaluatorTest {

    private final AgentEvaluator evaluator = new AgentEvaluator();

    @Test
    void toolCallF1_exactMatch_fullScore() {
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(Set.of("settingSearch"), Set.of("settingSearch"));
        assertEquals(1.0, m.precision(), 1e-9);
        assertEquals(1.0, m.recall(), 1e-9);
        assertEquals(1.0, m.f1(), 1e-9);
    }

    @Test
    void toolCallF1_partialMatch() {
        // expected 2 个、actual 2 个，交集 1 个 → precision=0.5, recall=0.5, f1=0.5
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(
                Set.of("settingSearch", "query_database"),
                Set.of("settingSearch", "web_search"));
        assertEquals(0.5, m.precision(), 1e-9);
        assertEquals(0.5, m.recall(), 1e-9);
        assertEquals(0.5, m.f1(), 1e-9);
    }

    @Test
    void toolCallF1_extraActualTool_lowersPrecision() {
        // 实际多调了不该调的工具：recall 满分、precision 下降
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(
                Set.of("settingSearch"),
                Set.of("settingSearch", "web_search"));
        assertEquals(0.5, m.precision(), 1e-9);
        assertEquals(1.0, m.recall(), 1e-9);
    }

    @Test
    void toolCallF1_missingExpectedTool_lowersRecall() {
        // 期望的工具没调：precision 满分、recall 下降
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(
                Set.of("settingSearch", "query_database"),
                Set.of("settingSearch"));
        assertEquals(1.0, m.precision(), 1e-9);
        assertEquals(0.5, m.recall(), 1e-9);
    }

    @Test
    void toolCallF1_bothEmpty_fullScore() {
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(Set.of(), Set.of());
        assertEquals(1.0, m.precision(), 1e-9);
        assertEquals(1.0, m.recall(), 1e-9);
        assertEquals(1.0, m.f1(), 1e-9);
    }

    @Test
    void toolCallF1_nullInputs_fullScoreOrSafe() {
        assertEquals(1.0, evaluator.toolCallF1(null, null).f1(), 1e-9);
        // expected 空 + actual 非空 → 视为「多余调用」：precision/recall/f1 均 0
        AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(null, Set.of("web_search"));
        assertEquals(0.0, m.precision(), 1e-9);
        assertEquals(0.0, m.recall(), 1e-9);
        assertEquals(0.0, m.f1(), 1e-9);
    }

    @Test
    void intentAccuracy_allCorrect() {
        assertEquals(1.0, evaluator.intentAccuracy(
                List.of("QUERY", "WRITE", "CHAT"),
                List.of("QUERY", "WRITE", "CHAT")), 1e-9);
    }

    @Test
    void intentAccuracy_partialCorrect() {
        assertEquals(2.0 / 3.0, evaluator.intentAccuracy(
                List.of("QUERY", "WRITE", "QUERY"),
                List.of("QUERY", "WRITE", "CHAT")), 1e-9);
    }

    @Test
    void intentAccuracy_sizeMismatch_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.intentAccuracy(List.of("QUERY"), List.of("QUERY", "WRITE")));
    }

    @Test
    void intentAccuracy_emptyExpected_returnsZero() {
        assertEquals(0.0, evaluator.intentAccuracy(List.of(), List.of()), 1e-9);
    }
}
