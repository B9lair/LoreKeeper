package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * 章节文件整本上传（可切多章）入库聚合结果。
 * 取代原先单章 {@link ChapterIngestResult} 作为 {@code /lore/chapter/upload-file} 的返回。
 *
 * @param chapterCount       实际入库章节数
 * @param chapters           每章明细（顺序与切分顺序一致）
 * @param totalNewSettings   各章新增设定合计
 * @param totalUpdatedSettings 各章追加设定合计
 * @param totalConflicts     各章冲突合计（已进待确认队列）
 * @param totalForeshadows   各章伏笔合计
 */
public record ChapterBatchIngestResult(
        int chapterCount,
        List<ChapterIngestResult> chapters,
        int totalNewSettings,
        int totalUpdatedSettings,
        int totalConflicts,
        int totalForeshadows
) {
}
