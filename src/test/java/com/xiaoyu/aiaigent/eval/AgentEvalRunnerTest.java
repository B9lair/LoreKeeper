package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.agent.IntentRouter;
import com.xiaoyu.aiaigent.agent.LoreAgentFactory;
import com.xiaoyu.aiaigent.agent.ToolCallAgent;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.rag.ChunkingService;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Agent 层评测运行器：真连 DB + LLM，走真实链路（IntentRouter → LoreAgentFactory → ToolCallAgent）拿
 * 实际工具序列 + 最终回答，算 Tool Call F1（确定性，{@link AgentEvaluator}）+ Goal Accuracy（LLM-as-judge 判 successCriteria）。
 * <p>
 * ⚠️ {@code @Disabled} 默认不进常规 mvn test（Agent 链路依赖 LLM，结果随机）。
 * 手动运行：临时删除 {@code @Disabled} 后执行 {@code mvn test -Dtest=AgentEvalRunnerTest}。
 */
@SpringBootTest
@Disabled("真连 LLM，需手动运行（Agent 链路依赖 LLM，结果随机，不放常规 CI）")
@Tag("llm")
class AgentEvalRunnerTest {

    @Resource private IntentRouter intentRouter;
    @Resource private LoreAgentFactory loreAgentFactory;
    @Resource private LlmJudgeService llmJudgeService;
    @Resource private ChunkingService chunkingService;
    @Resource private CharacterRepository characterRepository;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource(name = "pgVectorVectorStore") private VectorStore pgVectorVectorStore;

    private static final long TEST_USER_ID = 998401L;
    private static final long TEST_WORK_ID = 998402L;

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
    void evalAgent() {
        List<EvalDataset.AgentCase> cases = dataset.agent() == null ? List.of() : dataset.agent();
        assertFalse(cases.isEmpty(), "评测集 agent 样本不应为空");

        AgentEvaluator evaluator = new AgentEvaluator();
        double f1Sum = 0.0;
        int goalHits = 0;

        System.out.println("\n========== Agent 层评测：逐样本明细 ==========");
        for (int i = 0; i < cases.size(); i++) {
            EvalDataset.AgentCase c = cases.get(i);
            ToolCallAgent agent = runAgent(c.question());
            String answer = agent.getLastAnswer();
            answer = (answer == null || answer.isBlank()) ? "（Agent 未产生最终回答）" : answer;

            // 实际业务工具序列：过滤 doTerminate（流程性工具，不属于 expectedTools 的业务语义）
            Set<String> actualTools = new LinkedHashSet<>(agent.getToolCallSequence());
            actualTools.remove("doTerminate");
            Set<String> expectedTools = new LinkedHashSet<>(c.expectedTools());

            AgentEvaluator.ToolMetrics m = evaluator.toolCallF1(expectedTools, actualTools);
            f1Sum += m.f1();

            boolean goalHit = llmJudgeService.judgeGoal(c.question(), answer, c.successCriteria());
            if (goalHit) {
                goalHits++;
            }

            System.out.printf("[样本 %d] 问题：%s%n", i + 1, c.question());
            System.out.printf("  实际工具：%s | 期望工具：%s%n", actualTools, expectedTools);
            System.out.printf("  Tool Call F1：%s%n", m);
            System.out.printf("  回答：%s%n", answer);
            System.out.printf("  Goal 达成：%s（判据：%s）%n", goalHit ? "是" : "否", c.successCriteria());
            System.out.println();
        }

        int n = cases.size();
        System.out.println("========== Agent 层评测汇总 ==========");
        System.out.printf("Tool Call F1（平均）=%.4f%n", f1Sum / n);
        System.out.printf("Goal Accuracy=%.4f (%d/%d)%n", (double) goalHits / n, goalHits, n);
    }

    /** 真实链路：意图路由 → 装配 Agent → 同步 run，返回 Agent（供读取工具序列 + 最终回答） */
    private ToolCallAgent runAgent(String question) {
        TenantContext.Tenant tenant = new TenantContext.Tenant(TEST_USER_ID, TEST_WORK_ID);
        IntentRouter.RouteResult route = intentRouter.route(question);
        ToolCallAgent agent = loreAgentFactory.create(route.intent(), route.querySubType(), tenant, null);
        agent.run(question);
        return agent;
    }

    private void seed() {
        // 外键顺序：users → work → character
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "eval-agent@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "评测集作品-Agent");

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
