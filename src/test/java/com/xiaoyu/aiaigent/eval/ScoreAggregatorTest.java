package com.xiaoyu.aiaigent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreAggregatorTest {

    private final ScoreAggregator aggregator = new ScoreAggregator();

    @Test
    void recallAtK_fullHit_returnsOne() {
        assertEquals(1.0, ScoreAggregator.recallAtK(
                List.of("s1", "s2", "s3"), List.of("s1", "s3"), 5), 1e-9);
    }

    @Test
    void recallAtK_partialHit_returnsRatio() {
        assertEquals(0.5, ScoreAggregator.recallAtK(
                List.of("s1", "s9"), List.of("s1", "s3"), 5), 1e-9);
    }

    @Test
    void recallAtK_emptyExpected_returnsMinusOne() {
        assertEquals(-1.0, ScoreAggregator.recallAtK(
                List.of("s1"), List.of(), 5), 1e-9);
    }

    @Test
    void recallAtK_emptyRetrieved_returnsZero() {
        assertEquals(0.0, ScoreAggregator.recallAtK(
                List.of(), List.of("s1"), 5), 1e-9);
    }

    @Test
    void composite_allPresent_weightsSumToOne() {
        // 0.35*1.0 + 0.25*0.8 + 0.25*0.9 + 0.15*1.0
        double expected = 0.35 * 1.0 + 0.25 * 0.8 + 0.25 * 0.9 + 0.15 * 1.0;
        assertEquals(expected, aggregator.composite(1.0, 0.8, 0.9, 1.0), 1e-9);
    }

    @Test
    void composite_missingRecall_renormalizesRemainingWeights() {
        // recall 缺失(-1)：剩下 faithfulness/accuracy/relevancy 权重 0.25+0.25+0.15=0.65
        // (0.25*1.0 + 0.25*1.0 + 0.15*1.0) / 0.65 = 1.0
        assertEquals(1.0, aggregator.composite(-1.0, 1.0, 1.0, 1.0), 1e-9);
    }

    @Test
    void composite_allMissing_returnsZero() {
        assertEquals(0.0, aggregator.composite(-1.0, -1.0, -1.0, -1.0), 1e-9);
    }

    @Test
    void diagnose_retrievalOnly_whenRecallBelowOneButJudgeOk() {
        ScoreAggregator.Diagnosis d = aggregator.diagnose(
                List.of("s1", "s2"), 0.5, 1.0, 1.0, List.of(), List.of());
        assertEquals(ScoreAggregator.FailureKind.RETRIEVAL, d.kind());
    }

    @Test
    void diagnose_generationOnly_whenRecallFullButUnsupportedClaim() {
        ScoreAggregator.Diagnosis d = aggregator.diagnose(
                List.of("s1"), 1.0, 1.0, 1.0, List.of("凭空捏造的设定"), List.of());
        assertEquals(ScoreAggregator.FailureKind.GENERATION, d.kind());
    }

    @Test
    void diagnose_both_whenRecallLowAndFaithfulnessLow() {
        ScoreAggregator.Diagnosis d = aggregator.diagnose(
                List.of("s1", "s2"), 0.5, 0.5, 0.9, List.of(), List.of());
        assertEquals(ScoreAggregator.FailureKind.BOTH, d.kind());
    }

    @Test
    void diagnose_none_whenAllPass() {
        ScoreAggregator.Diagnosis d = aggregator.diagnose(
                List.of("s1"), 1.0, 1.0, 1.0, List.of(), List.of());
        assertEquals(ScoreAggregator.FailureKind.NONE, d.kind());
    }

    @Test
    void diagnose_negativeSample_noGoldenContext_skipsRetrievalFault() {
        // 负样本 goldenContextIds 为空：即使 recall=-1 也不判检索掉分
        ScoreAggregator.Diagnosis d = aggregator.diagnose(
                List.of(), -1.0, 1.0, 1.0, List.of(), List.of());
        assertEquals(ScoreAggregator.FailureKind.NONE, d.kind());
    }
}
