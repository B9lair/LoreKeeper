package com.xiaoyu.aiaigent.lore;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 实体抽取器：LLM 结构化输出「设定 / 更新 / 伏笔 / 时间线」。
 * 抽到后立即做 evidence 过滤
 * 反幻觉兜底，代码层，不靠 prompt
 */
@Component
public class EntityExtractor {

    private final ChatClient chatClient;

    public EntityExtractor(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    private static final String SYSTEM_PROMPT = """
            你是小说设定抽取器。从给定章节抽取「持久设定」「伏笔」「时间线事件」三类。
            判定标准：
            - 设定：描述世界/角色的持久状态或规则，不依赖当前情节的一次性动作。
            - 伏笔：暗示未来、留有悬念、当前未解释。
            - 剧情：正在发生的一次性动作 → 不抽。
            - 描写：外貌/场景描写，不构成持久约束 → 不抽。

            正例（要抽）：
            - "青阳城靠灵石阵供暖" → 设定 entity=青阳城, attribute=供暖方式, value=灵石阵
            - "主角天生左撇子" → 设定 entity=主角, attribute=惯用手, value=左撇子
            - "她总觉得这事没那么简单" → 伏笔

            反例（不抽）：
            - "他推门走了进去" → 剧情，不抽
            - "她有一双大眼睛" → 描写，不抽

            输出结构：
            settings[]: {entity, attribute, value, evidence}
            settingUpdates[]: {entity, attribute, newValue, evidence}
            foreshadows[]: {content, evidence}
            timeline[]: {timePoint, event, evidence}

            硬性约束：① 只抽明确写出的，禁止脑补；② 每条必带 evidence（原文原句），为空会被丢弃；③ 拿不准的一律不抽（宁可漏，不脑补）。""";

    public ChapterExtraction extract(String chapterText) {
        ChapterExtraction ex = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(chapterText)
                .call()
                .entity(ChapterExtraction.class);
        return filterEmptyEvidence(ex);
    }

    /**
     * 反幻觉兜底：丢弃 evidence 为空/空白的条目（纯函数，可独立单测）。
     */
    static ChapterExtraction filterEmptyEvidence(ChapterExtraction ex) {
        if (ex == null) {
            return ChapterExtraction.empty();
        }
        return new ChapterExtraction(
                filterSettings(ex.settings()),
                filterUpdates(ex.settingUpdates()),
                filterForeshadows(ex.foreshadows()),
                filterTimeline(ex.timeline()));
    }

    private static List<NewSetting> filterSettings(List<NewSetting> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(s -> isNotBlank(s.evidence()))
                .toList();
    }

    private static List<SettingUpdate> filterUpdates(List<SettingUpdate> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(s -> isNotBlank(s.evidence()))
                .toList();
    }

    private static List<Foreshadow> filterForeshadows(List<Foreshadow> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(f -> isNotBlank(f.evidence()))
                .toList();
    }

    private static List<TimelineEvent> filterTimeline(List<TimelineEvent> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> isNotBlank(t.evidence()))
                .toList();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
