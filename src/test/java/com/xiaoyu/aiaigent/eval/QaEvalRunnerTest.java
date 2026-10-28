package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.agent.LoreAgentFactory;
import com.xiaoyu.aiaigent.agent.ToolCallAgent;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.rag.HybridSearchService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.CharacterRepository.Character;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 端到端问答评测运行器：真连 DB + LLM，走真实链路（LoreAgentFactory → ToolCallAgent）拿最终回答，
 * 再用 LlmJudgeService 打 Faithfulness / Answer Relevancy / Answer Accuracy 三指标（归因层 + 回答层）。
 * <p>
 * ⚠️ LLM 结果随机、慢、费 key，故 {@code @Disabled} 默认不进常规 mvn test（与 ExtractionEvalRunnerTest 约定一致）。
 * 手动运行：临时删除 {@code @Disabled} 注解后执行 {@code mvn test -Dtest=QaEvalRunnerTest}。
 */
@SpringBootTest
@Disabled("真连 LLM，需手动运行（LLM 结果随机，不放常规 CI）")
@Tag("llm")
class QaEvalRunnerTest {

    @Resource private LoreAgentFactory loreAgentFactory;
    @Resource private HybridSearchService hybridSearchService;
    @Resource private LlmJudgeService llmJudgeService;
    @Resource private ChunkingService chunkingService;
    @Resource private CharacterRepository characterRepository;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource(name = "pgVectorVectorStore") private VectorStore pgVectorVectorStore;

    private static final long TEST_USER_ID = 998301L;
    private static final long TEST_WORK_ID = 998302L;

    private EvalDataset dataset;

    @BeforeEach
    void setup() {
        dataset = EvalDatasetLoader.load();
        cleanup();
        seed();
    }

    @AfterEach
    void teardown() {
        cleanup();
    }

    @Test
    void evalQa() {
        List<EvalDataset.QaCase> cases = dataset.qa() == null ? List.of() : dataset.qa();
        assertFalse(cases.isEmpty(), "评测集 qa 样本不应为空");

        ScoreAggregator aggregator = new ScoreAggregator();
        List<SampleResult> results = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            EvalDataset.QaCase c = cases.get(i);
            String answer = askAgent(c.question());

            List<Document> docs = hybridSearchService.search(c.question(), TEST_WORK_ID, 10);
            String context = toContext(docs);
            List<String> retrievedIds = RagEvaluator.entityIdsOf(docs);
            double recall = ScoreAggregator.recallAtK(retrievedIds, c.goldenContextIds(), 10);

            LlmJudgeService.JudgeVerdict v = llmJudgeService.judge(
                    c.question(), answer, context, c.referenceAnswer(), c.badCases());

            double composite = aggregator.composite(recall, v.faithfulness(), v.answerAccuracy(), v.answerRelevancy());
            ScoreAggregator.Diagnosis diag = aggregator.diagnose(
                    c.goldenContextIds(), recall, v.faithfulness(), v.answerAccuracy(),
                    v.unsupportedClaims(), v.badCaseViolations());

            results.add(new SampleResult(i + 1, c, answer, recall, v, composite, diag));
        }

        printDetails(results);
        printSummary(results);
        printReplay(results);
    }

    /** 真实链路：装配统一 Agent → 同步 run，取最终回答（与 AiController.doChatWithLore 同链路，run 替 runstream） */
    private String askAgent(String question) {
        TenantContext.Tenant tenant = new TenantContext.Tenant(TEST_USER_ID, TEST_WORK_ID);
        ToolCallAgent agent = loreAgentFactory.create(tenant, null);
        agent.run(question);
        String answer = agent.getLastAnswer();
        return (answer == null || answer.isBlank()) ? "（Agent 未产生最终回答）" : answer;
    }

    /** 检索上下文：直接复用混合检索（与 Agent 内 settingSearch 同源），供 judge 判定 faithfulness */
    private String toContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "（未检索到相关内容）";
        }
        return docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
    }

    /** 单样本评测结果（供逐样本明细、汇总、失败回放三份报告共用） */
    private record SampleResult(
            int index,
            EvalDataset.QaCase qaCase,
            String answer,
            double recall,
            LlmJudgeService.JudgeVerdict verdict,
            double composite,
            ScoreAggregator.Diagnosis diagnosis) {
    }

    private void printDetails(List<SampleResult> results) {
        System.out.println("\n========== 端到端问答评测：逐样本明细 ==========");
        for (SampleResult r : results) {
            EvalDataset.QaCase c = r.qaCase();
            System.out.printf("[样本 %d] 问题：%s（难度 %s）%n", r.index(), c.question(), c.difficulty());
            System.out.printf("  回答：%s%n", r.answer());
            System.out.printf("  recall=%.2f faithfulness=%.2f relevancy=%.2f accuracy=%.2f | 加权总分=%.3f%n",
                    r.recall(), r.verdict().faithfulness(), r.verdict().answerRelevancy(),
                    r.verdict().answerAccuracy(), r.composite());
            if (r.verdict().badCaseViolations() != null && !r.verdict().badCaseViolations().isEmpty()) {
                System.out.printf("  [幻觉违规(badCases)] %s%n", r.verdict().badCaseViolations());
            }
            if (r.verdict().unsupportedClaims() != null && !r.verdict().unsupportedClaims().isEmpty()) {
                System.out.printf("  [judge 判定的无据 claim] %s%n", r.verdict().unsupportedClaims());
            }
            System.out.println();
        }
    }

    private void printSummary(List<SampleResult> results) {
        int n = results.size();
        double faithSum = 0.0, relevancySum = 0.0, accuracySum = 0.0, recallSum = 0.0, compositeSum = 0.0;
        int accuracyCount = 0, recallCount = 0;

        for (SampleResult r : results) {
            faithSum += r.verdict().faithfulness();
            relevancySum += r.verdict().answerRelevancy();
            if (r.verdict().answerAccuracy() >= 0) {
                accuracySum += r.verdict().answerAccuracy();
                accuracyCount++;
            }
            if (r.recall() >= 0) {
                recallSum += r.recall();
                recallCount++;
            }
            compositeSum += r.composite();
        }

        System.out.println("========== 端到端问答评测汇总 ==========");
        System.out.printf("ContextRecall=%.4f (%d/%d 有 goldenContextIds)%n",
                recallCount == 0 ? -1.0 : recallSum / recallCount, recallCount, n);
        System.out.printf("Faithfulness=%.4f | AnswerRelevancy=%.4f | AnswerAccuracy=%.4f (%d/%d 有参考答案)%n",
                faithSum / n, relevancySum / n,
                accuracyCount == 0 ? -1.0 : accuracySum / accuracyCount, accuracyCount, n);
        System.out.printf("加权总分（整体均值）=%.4f%n", compositeSum / n);
    }

    private void printReplay(List<SampleResult> results) {
        List<SampleResult> failed = results.stream()
                .filter(r -> r.diagnosis().kind() != ScoreAggregator.FailureKind.NONE
                        || r.composite() < ScoreAggregator.FAIL_THRESHOLD)
                .toList();

        System.out.println("========== 失败样本回放（检索掉分 vs 生成掉分） ==========");
        if (failed.isEmpty()) {
            System.out.println("（无失败样本：全部样本加权总分达标且无幻觉/无据 claim）");
            return;
        }
        for (SampleResult r : failed) {
            EvalDataset.QaCase c = r.qaCase();
            System.out.printf("[样本 %d] %s（难度 %s）%n", r.index(), c.question(), c.difficulty());
            System.out.printf("  recall=%.2f faith=%.2f acc=%.2f rel=%.2f | 加权总分=%.3f%n",
                    r.recall(), r.verdict().faithfulness(), r.verdict().answerAccuracy(),
                    r.verdict().answerRelevancy(), r.composite());
            System.out.printf("  定位：%s — %s%n", r.diagnosis().kind(), r.diagnosis().reason());
            if (r.verdict().unsupportedClaims() != null && !r.verdict().unsupportedClaims().isEmpty()) {
                System.out.printf("  无据claim：%s%n", r.verdict().unsupportedClaims());
            }
            if (r.verdict().badCaseViolations() != null && !r.verdict().badCaseViolations().isEmpty()) {
                System.out.printf("  幻觉违规：%s%n", r.verdict().badCaseViolations());
            }
            System.out.println();
        }
        System.out.printf("共 %d 个失败样本（检索掉分 vs 生成掉分详见各样本「定位」）%n", failed.size());
    }

    private void seed() {
        // 外键顺序：users → work → character
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "eval-qa@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "评测集作品-QA");

        for (EvalDataset.CharacterSeed c : dataset.seed().characters()) {
            characterRepository.save(Character.of(TEST_WORK_ID, c.name(), c.aliases(), "角色", null, 1, "alive"));
        }

        List<Document> docs = dataset.seed().settings().stream()
                .map(s -> chunkingService.settingToDocument(
                        TEST_WORK_ID, Long.parseLong(s.id()), s.chapterNo(), s.type(), s.text()))
                .toList();
        pgVectorVectorStore.add(docs);
    }

    /** 按外键逆序清理：character → vector_store → work → users */
    private void cleanup() {
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'workId' = ?", String.valueOf(TEST_WORK_ID));
        jdbcTemplate.update("DELETE FROM work WHERE id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }
}
