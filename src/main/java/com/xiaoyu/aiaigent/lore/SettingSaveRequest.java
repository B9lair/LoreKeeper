package com.xiaoyu.aiaigent.lore;

/**
 * /setting/save 请求体。
 * id 为 null = 新建；非 null = 更新。
 */
public record SettingSaveRequest(
        Long id,                   // null=新建，非 null=更新
        String entity,             // 必填
        String attribute,          // 必填
        String value,              // 必填
        String type,               // 可选，默认 "rule"
        String evidence,           // 可选（手动加的可为空）
        Integer establishedChapter, // 可选
        Boolean scanConflict       // 可选，是否触发冲突扫描，默认 true
) {
}
