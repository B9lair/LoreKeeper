package com.xiaoyu.aiaigent.eval;

import com.xiaoyu.aiaigent.lore.NewSetting;
import com.xiaoyu.aiaigent.lore.SettingMergeService;
import com.xiaoyu.aiaigent.lore.SettingMergeService.MergeResult;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.CharacterRepository.Character;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 一致性检测评测运行器：真连 DB，人工注入矛盾对（历史断言 valueA + 候选断言 valueB），
 * 走 SettingMergeService 归并链路，用混淆矩阵算 F1 / 准确率。
 * <p>
 * 只评「归并判定」这一确定性环节（不依赖 LLM 抽取），可稳定进 mvn test。
 */
@SpringBootTest
class ConsistencyEvalRunnerTest {

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private CharacterRepository characterRepository;
    @Resource private WorldSettingRepository worldSettingRepository;

    private static final long TEST_USER_ID = 998101L;
    private static final long TEST_WORK_ID = 998102L;

    private EvalDataset dataset;
    private SettingMergeService mergeService;

    @BeforeEach
    void setup() {
        dataset = EvalDatasetLoader.load();
        cleanup();
        jdbcTemplate.update("INSERT INTO users (id, email, password_hash) VALUES (?, ?, ?)",
                TEST_USER_ID, "eval-consistency@example.com", "x");
        jdbcTemplate.update("INSERT INTO work (id, user_id, title) VALUES (?, ?, ?)",
                TEST_WORK_ID, TEST_USER_ID, "评测集作品-一致性");

        // 预插角色，让归并里「实体已知」成立（entityKnown）
        for (EvalDataset.CharacterSeed c : dataset.seed().characters()) {
            characterRepository.save(Character.of(TEST_WORK_ID, c.name(), c.aliases(), "角色", null, 1, "alive"));
        }

        mergeService = new SettingMergeService(characterRepository, worldSettingRepository);
    }

    @AfterEach
    void teardown() {
        cleanup();
    }

    @Test
    void evalConsistency() {
        List<Boolean> predicted = new ArrayList<>();
        List<Boolean> expected = new ArrayList<>();

        for (EvalDataset.ConsistencyCase c : dataset.consistency()) {
            // 每个样本独立：清掉该实体的历史断言，避免同实体多样本串扰
            jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ? AND entity = ?",
                    TEST_WORK_ID, c.entity());

            // 历史断言 valueA 已确立（第 3 章）
            worldSettingRepository.save(WorldSetting.of(
                    TEST_WORK_ID, "person", c.entity() + " 的 " + c.attribute() + "：" + c.valueA(),
                    c.entity(), c.attribute(), c.valueA(), "第3章历史断言", 3, 1));

            // 候选断言 valueB（第 50 章）走归并
            NewSetting candidate = new NewSetting(c.entity(), c.attribute(), c.valueB(), "第50章候选断言");
            MergeResult result = mergeService.merge(candidate, TEST_WORK_ID, 50);

            predicted.add(result == MergeResult.CONFLICT);
            expected.add(c.isConflict());
        }

        ConsistencyEvaluator.ConsistencyMetrics metrics =
                new ConsistencyEvaluator().evaluate(predicted, expected);

        System.out.println(">>> 一致性检测评测结果：" + metrics);
        assertFalse(dataset.consistency().isEmpty(), "评测集 consistency 样本不应为空");
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM character WHERE work_id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM work WHERE id = ?", TEST_WORK_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }
}
