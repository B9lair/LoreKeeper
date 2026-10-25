package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.rag.HybridSearchService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.CharacterRepository.Character;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * RAG 评测运行器：真连 DB + 向量库，加载 eval/dataset.json 灌种子 → 逐 query 检索 → 算 Recall@5 / MRR。
 * 对应方案 3.4「验证：mvn test 输出 Recall@5、MRR」。
 */
@SpringBootTest
class RagEvalRunnerTest {

    @Resource private HybridSearchService hybridSearchService;
    @Resource private ChunkingService chunkingService;
    @Resource private CharacterRepository characterRepository;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource(name = "pgVectorVectorStore") private VectorStore pgVectorVectorStore;

    private static final long TEST_USER_ID = 998001L;
    private static final long TEST_WORK_ID = 998002L;

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
    void evalRag() {
        List<RagEvaluator.RagSample> samples = dataset.rag().stream()
                .map(r -> new RagEvaluator.RagSample(r.query(), r.expectedIds()))
                .toList();
        List<List<String>> retrieved = dataset.rag().stream()
                .map(r -> RagEvaluator.entityIdsOf(hybridSearchService.search(r.query(), TEST_WORK_ID, 5)))
                .toList();

        RagEvaluator.RagMetrics metrics = new RagEvaluator().evaluate(samples, retrieved, 5);

        System.out.println(">>> RAG 评测结果：" + metrics);
        assertFalse(dataset.rag().isEmpty(), "评测集 rag 样本不应为空");
    }

    private void seed() {
        // 外键顺序：users → work → character
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "eval-rag@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "评测集作品-RAG");

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
