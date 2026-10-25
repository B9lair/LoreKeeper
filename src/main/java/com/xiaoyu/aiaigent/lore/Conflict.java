package com.xiaoyu.aiaigent.lore;

/**
 * 一致性矛盾
 * 某实体在 A/B 两章存在互相冲突的断言
 */
public record Conflict(
        String entity,
        String descA,
        String descB,
        String chapterA,
        String chapterB
) {
}
