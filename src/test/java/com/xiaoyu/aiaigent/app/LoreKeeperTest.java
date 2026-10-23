package com.xiaoyu.aiaigent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoreKeeperTest {

    @Resource
    private LoreKeeper loreKeeper;

    // 占位：恋爱用例已删除，后续步骤补全创作用例（设定问答 / 一致性审查 / 伏笔追踪）
    @Test
    void contextLoads() {
        Assertions.assertNotNull(loreKeeper);
    }
}
