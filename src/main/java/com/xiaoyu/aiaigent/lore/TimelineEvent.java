package com.xiaoyu.aiaigent.lore;

/**
 * 抽取出的时间线事件
 */
public record TimelineEvent(
        String timePoint,   // 时间点
        String event,       // 事件
        String evidence     // 证据
) {
}
