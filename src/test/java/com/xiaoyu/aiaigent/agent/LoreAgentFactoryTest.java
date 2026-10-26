package com.xiaoyu.aiaigent.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoreAgentFactoryTest {

    @Test
    void preferredTypes_onlyForQuerySetting() {
        // QUERY + SETTING → 优先设定/伏笔（不足补章节）
        assertEquals(Set.of("setting", "foreshadow"),
                LoreAgentFactory.preferredTypes(IntentRouter.Intent.QUERY, IntentRouter.QuerySubType.SETTING));
        // QUERY + CHAPTER → 全量（章节为主，不启用优先）
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.QUERY, IntentRouter.QuerySubType.CHAPTER));
        // QUERY + 子类型降级 null → 全量兜底
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.QUERY, null));
        // 其他意图 → 全量（现状不变）
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.WRITE, null));
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.REVIEW, null));
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.FORESHADOW, null));
        assertNull(LoreAgentFactory.preferredTypes(IntentRouter.Intent.CHAT, null));
    }
}
