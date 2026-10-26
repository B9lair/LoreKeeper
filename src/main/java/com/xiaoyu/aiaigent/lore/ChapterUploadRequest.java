package com.xiaoyu.aiaigent.lore;

/**
 * /lore/chapter/upload 请求体。
 */
public record ChapterUploadRequest(
        String title,       // 章节标题，可为空
        String chapterText  // 章节正文（纯文本 / TXT / MD 读成字符串）
) {
}
