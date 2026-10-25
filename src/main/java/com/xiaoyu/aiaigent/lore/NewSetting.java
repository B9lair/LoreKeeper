package com.xiaoyu.aiaigent.lore;

/**
 * 新设定
 * 抽取出的新确立设定断言
 */
public record NewSetting(
        String entity,    // 实体
        String attribute, // 属性
        String value,     // 值
        String evidence   // 原文证据
) {
}
