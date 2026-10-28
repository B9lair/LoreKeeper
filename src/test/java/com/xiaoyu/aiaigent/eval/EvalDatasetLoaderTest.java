package com.xiaoyu.aiaigent.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 eval/dataset.json 能完整反序列化为 5 类样本（纯单元测试，不连 DB/LLM，进 CI）。
 * 作用是兜住「新增 agent 字段后 JSON 结构写错」这类静默问题——运行器拿到空列表不报错但会误判。
 */
class EvalDatasetLoaderTest {

    @Test
    void load_parsesAllFiveSections() {
        EvalDataset d = EvalDatasetLoader.load();

        assertNotNull(d.seed());
        assertFalse(d.seed().settings().isEmpty());
        assertFalse(d.seed().characters().isEmpty());

        assertFalse(d.rag().isEmpty());
        assertFalse(d.consistency().isEmpty());
        assertFalse(d.extraction().isEmpty());

        // qa：10 条（7 正 + 3 负）
        assertEquals(10, d.qa().size());

        // agent：5 条，每条都有 expectedTools + successCriteria
        assertEquals(5, d.agent().size());
        for (EvalDataset.AgentCase a : d.agent()) {
            assertFalse(a.expectedTools().isEmpty(), "expectedTools 不应为空");
            assertFalse(a.successCriteria().isBlank(), "successCriteria 不应为空");
        }
    }
}
