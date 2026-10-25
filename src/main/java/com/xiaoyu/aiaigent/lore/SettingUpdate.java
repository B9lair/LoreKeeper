package com.xiaoyu.aiaigent.lore;

/**
 * 对已有设定的更新/潜在冲突
 * 注：2.4 主流程暂只消费 NewSetting，本类型预留（见 2.6.1）。
 */
public record SettingUpdate(
        String entity,    // 实体
        String attribute, // 属性
        String newValue,  // 新值
        String evidence   // 证据
) {
}
