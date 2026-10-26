package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * /setting/save 响应体：落库结果 + 冲突扫描清单。
 */
public record SettingSaveResult(
        long id,
        int version,
        List<ConflictScanItem> conflicts   // 冲突扫描结果（scanConflict=false 时为空）
) {
}
