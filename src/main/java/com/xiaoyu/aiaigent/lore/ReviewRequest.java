package com.xiaoyu.aiaigent.lore;

/**
 * /lore/review 请求体。
 */
public record ReviewRequest(
        Integer chapterNo,
        String chapterText
) {
}
