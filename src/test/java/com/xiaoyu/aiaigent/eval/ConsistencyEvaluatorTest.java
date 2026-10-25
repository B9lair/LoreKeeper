package com.xiaoyu.aiaigent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyEvaluatorTest {

    private final ConsistencyEvaluator evaluator = new ConsistencyEvaluator();

    @Test
    void evaluate_computesConfusionMatrixAndF1() {
        // predicted / expected 按下标对齐
        List<Boolean> predicted = List.of(true, true, false, false);
        List<Boolean> expected = List.of(true, false, true, false);

        ConsistencyEvaluator.ConsistencyMetrics m = evaluator.evaluate(predicted, expected);

        assertEquals(1, m.tp()); // true/true
        assertEquals(1, m.fp()); // true/false
        assertEquals(1, m.fn()); // false/true
        assertEquals(1, m.tn()); // false/false

        assertEquals(0.5, m.precision(), 1e-9); // 1/(1+1)
        assertEquals(0.5, m.recall(), 1e-9);    // 1/(1+1)
        assertEquals(0.5, m.f1(), 1e-9);        // 2*0.5*0.5/1.0
        assertEquals(0.5, m.accuracy(), 1e-9);  // (1+1)/4
    }

    @Test
    void evaluate_allCorrect_fullScore() {
        List<Boolean> predicted = List.of(true, false, true);
        List<Boolean> expected = List.of(true, false, true);

        ConsistencyEvaluator.ConsistencyMetrics m = evaluator.evaluate(predicted, expected);

        assertEquals(2, m.tp());
        assertEquals(0, m.fp());
        assertEquals(0, m.fn());
        assertEquals(1, m.tn());
        assertEquals(1.0, m.precision(), 1e-9);
        assertEquals(1.0, m.recall(), 1e-9);
        assertEquals(1.0, m.f1(), 1e-9);
        assertEquals(1.0, m.accuracy(), 1e-9);
    }

    @Test
    void evaluate_zeroPrecisionAndRecall_yieldsZeroF1() {
        List<Boolean> predicted = List.of(false);
        List<Boolean> expected = List.of(false);

        ConsistencyEvaluator.ConsistencyMetrics m = evaluator.evaluate(predicted, expected);

        assertEquals(0.0, m.precision(), 1e-9);
        assertEquals(0.0, m.recall(), 1e-9);
        assertEquals(0.0, m.f1(), 1e-9);
        assertEquals(1.0, m.accuracy(), 1e-9); // TN 命中
    }

    @Test
    void evaluate_sizeMismatch_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(List.of(true), List.of()));
    }
}
