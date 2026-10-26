package com.xiaoyu.aiaigent.lore;

/**
 * 章节上传入库结果，含抽取统计（供前端上传页展示「本章抽到了什么」）。
 */
public record ChapterIngestResult(
        long chapterId,
        int seq,
        String title,
        int newSettings,     // 归并判定 NEW 的新增设定
        int updatedSettings, // 归并判定 UPDATE 的追加设定
        int conflicts,       // 归并判定 CONFLICT 的冲突（已进待确认队列）
        int foreshadows      // 抽取出的伏笔数
) {
}
