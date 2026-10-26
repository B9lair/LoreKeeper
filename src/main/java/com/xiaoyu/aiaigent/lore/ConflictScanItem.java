package com.xiaoyu.aiaigent.lore;

/**
 * 冲突扫描结果条目：改设定后，某章节原文与新设定矛盾。
 * isConflict 由 LLM 输出，代码层再过滤一次（反幻觉兜底，不靠 prompt）。
 */
public record ConflictScanItem(
        String chapterNo,   // 矛盾所在章节
        String excerpt,     // 原文矛盾原句
        String reason,      // 矛盾原因
        boolean isConflict  // LLM 判定是否矛盾
) {
}
