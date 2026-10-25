package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * 一致性审查结果
 * 矛盾清单 + 未回收伏笔提醒
 */
public record ConsistencyReport(
        List<Conflict> conflicts,
        List<String> foreshadowReminders
) {
}
