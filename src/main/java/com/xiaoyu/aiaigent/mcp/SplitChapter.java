package com.xiaoyu.aiaigent.mcp;

/**
 * doc-parser {@code split_chapters} 工具返回的单个章节切片。
 * 字段与工具返回 JSON 对齐（{@code title} / {@code content}），供 Jackson 直接反序列化。
 *
 * @param title   章节标题行（如「第一章 青阳城」）；无章节标题时工具返回「全文」
 * @param content 该章正文（纯文本）
 */
public record SplitChapter(String title, String content) {
}
