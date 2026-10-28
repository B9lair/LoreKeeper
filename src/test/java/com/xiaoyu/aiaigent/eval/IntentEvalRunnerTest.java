package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.agent.IntentRouter;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 意图路由评测运行器：真连 LLM，逐条调 {@link IntentRouter#route} 拿预测意图，与标注比对算 Intent Accuracy。
 * <p>
 * ⚠️ {@code @Disabled} 默认不进常规 mvn test（意图路由依赖 LLM，结果随机）。
 * 手动运行：临时删除 {@code @Disabled} 后执行 {@code mvn test -Dtest=IntentEvalRunnerTest}。
 */
@SpringBootTest
@Disabled("真连 LLM，需手动运行（意图路由依赖 LLM，结果随机，不放常规 CI）")
@Tag("llm")
class IntentEvalRunnerTest {

    @Resource private IntentRouter intentRouter;

    private EvalDataset dataset;

    @BeforeEach
    void setup() {
        dataset = EvalDatasetLoader.load();
    }

    @Test
    void evalIntent() {
        List<EvalDataset.IntentCase> cases = dataset.intent() == null ? List.of() : dataset.intent();
        assertFalse(cases.isEmpty(), "评测集 intent 样本不应为空");

        List<String> predicted = new ArrayList<>();
        List<String> expected = new ArrayList<>();

        System.out.println("\n========== 意图路由评测：逐样本明细 ==========");
        for (int i = 0; i < cases.size(); i++) {
            EvalDataset.IntentCase c = cases.get(i);
            IntentRouter.RouteResult r = intentRouter.route(c.message());
            String pred = r.intent().name();
            predicted.add(pred);
            expected.add(c.expectedIntent());
            boolean hit = c.expectedIntent().equals(pred);
            System.out.printf("[样本 %d] %s → 预测 %s（标注 %s）%s%n",
                    i + 1, c.message(), pred, c.expectedIntent(), hit ? "正确" : "误判");
        }

        double acc = new AgentEvaluator().intentAccuracy(predicted, expected);
        System.out.println("========== 意图路由评测汇总 ==========");
        System.out.printf("Intent Accuracy=%.4f (%d 样本)%n", acc, cases.size());
    }
}
