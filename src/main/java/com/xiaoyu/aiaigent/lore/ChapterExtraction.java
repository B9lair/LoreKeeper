package com.xiaoyu.aiaigent.lore;

import java.util.List;

/**
 * 一个章节的抽取结果：三类投影（设定 / 更新 / 伏笔 / 时间线）+ 角色名单。
 */
public record ChapterExtraction(
        List<NewSetting> settings,          // 设定
        List<SettingUpdate> settingUpdates, // 更新
        List<Foreshadow> foreshadows,       // 伏笔
        List<TimelineEvent> timeline,       // 时间线
        List<CharacterRef> characters       // 角色名单（标准名 + 别名，维护 character 表）
) {
    public static ChapterExtraction empty() {
        return new ChapterExtraction(List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
