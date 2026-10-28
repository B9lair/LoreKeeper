package com.xiaoyu.aiaigent.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluatorTest {

    private final RagEvaluator evaluator = new RagEvaluator();

    @Test
    void evaluate_computesRecallAndMrr() {
        List<RagEvaluator.RagSample> samples = List.of(
                new RagEvaluator.RagSample("q1", List.of("s1", "s2")),
                new RagEvaluator.RagSample("q2", List.of("s4")),
                new RagEvaluator.RagSample("q3", List.of("s6"))
        );
        List<List<String>> retrieved = List.of(
                List.of("s1", "s3", "s2"),   // q1: 命中 s1(rank1)、s2(rank3)，Recall=1.0，RR=1/1
                List.of("s5", "s4"),         // q2: 命中 s4(rank2)，Recall=1.0，RR=1/2
                List.of("s7", "s8")          // q3: 未命中，Recall=0，RR=0
        );

        RagEvaluator.RagMetrics m = evaluator.evaluate(samples, retrieved, 3);

        // Recall@3 = (1.0 + 1.0 + 0.0) / 3
        assertEquals(2.0 / 3.0, m.recallAtK(), 1e-9);
        // MRR = (1.0 + 0.5 + 0.0) / 3
        assertEquals(0.5, m.mrr(), 1e-9);
        assertEquals(3, m.queryCount());
        assertEquals(3, m.k());
    }

    @Test
    void evaluate_mrrZeroWhenNoHit() {
        List<RagEvaluator.RagSample> samples = List.of(
                new RagEvaluator.RagSample("q", List.of("s1"))
        );
        List<List<String>> retrieved = List.of(List.of("s2", "s3"));

        RagEvaluator.RagMetrics m = evaluator.evaluate(samples, retrieved, 5);

        assertEquals(0.0, m.recallAtK(), 1e-9);
        assertEquals(0.0, m.mrr(), 1e-9);
    }

    @Test
    void entityIdsOf_extractsEntityIdFromMetadata() {
        Document d1 = Document.builder().id("uuid-1").text("a").metadata(Map.of("entityId", "9001", "workId", "1")).build();
        Document d2 = Document.builder().id("uuid-2").text("b").metadata(Map.of("entityId", 9002, "workId", "1")).build();
        Document d3 = Document.builder().id("uuid-3").text("c").metadata(Map.of("workId", "1")).build();

        List<String> ids = RagEvaluator.entityIdsOf(List.of(d1, d2, d3));

        assertEquals(Arrays.asList("9001", "9002", null), ids);
    }

    @Test
    void evaluate_emptySamples_returnsZeroMetrics() {
        RagEvaluator.RagMetrics m = evaluator.evaluate(List.of(), List.of(), 5);
        assertEquals(0.0, m.recallAtK(), 1e-9);
        assertEquals(0.0, m.mrr(), 1e-9);
    }

    @Test
    void evaluate_sizeMismatch_throws() {
        List<RagEvaluator.RagSample> samples = List.of(new RagEvaluator.RagSample("q", List.of("s1")));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(samples, List.of(), 5));
    }

    @Test
    void contextPrecisionAtK_computesPurity() {
        // 前 3 个结果命中 2 个 → 2/3
        double p1 = evaluator.contextPrecisionAtK(
                List.of("s1", "s3", "s2", "s9"), List.of("s1", "s2"), 3);
        assertEquals(2.0 / 3.0, p1, 1e-9);

        // 前 K 全部命中 → 1.0
        double p2 = evaluator.contextPrecisionAtK(
                List.of("s1", "s2"), List.of("s1", "s2"), 5);
        assertEquals(1.0, p2, 1e-9);

        // 前 K 全部未命中 → 0.0
        double p3 = evaluator.contextPrecisionAtK(
                List.of("s7", "s8"), List.of("s1"), 5);
        assertEquals(0.0, p3, 1e-9);
    }

    @Test
    void contextPrecisionAtK_emptyOrNull_returnsZero() {
        assertEquals(0.0, evaluator.contextPrecisionAtK(List.of(), List.of("s1"), 5), 1e-9);
        assertEquals(0.0, evaluator.contextPrecisionAtK(null, List.of("s1"), 5), 1e-9);
    }
}
