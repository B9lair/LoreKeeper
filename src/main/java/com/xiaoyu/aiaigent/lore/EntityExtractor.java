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
            你是小说设定抽取器。从给定章节抽取「持久设定」「对已有设定的更新」「伏笔」「时间线事件」四类。
            判定标准：
            - 设定（settings）：描述世界/角色的持久状态或规则，不依赖当前情节的一次性动作。
            - 更新（settingUpdates）：本章出现的信息，看起来在更新/推翻某个"很可能已确立过"的稳定设定（如惯用手、身份、出身、能力），填入 newValue。
            - 伏笔：暗示未来、留有悬念、当前未解释。
            - 剧情：正在发生的一次性动作 → 不抽。
            - 描写：外貌/场景描写，不构成持久约束 → 不抽。

            正例（要抽）：
            - "青阳城靠灵石阵供暖" → settings {entity=青阳城, attribute=供暖方式, value=灵石阵}
            - "主角天生左撇子" → settings {entity=主角, attribute=惯用手, value=左撇子}
            - "主角如今改用右手持剑" → settingUpdates {entity=主角, attribute=惯用手, newValue=右手}
            - "她总觉得这事没那么简单" → 伏笔

            反例（不抽）：
            - "他推门走了进去" → 剧情，不抽
            - "她有一双大眼睛" → 描写，不抽

            输出结构：
            settings[]: {entity, attribute, value, evidence}
            settingUpdates[]: {entity, attribute, newValue, evidence}
            foreshadows[]: {content, evidence}
            timeline[]: {timePoint, event, evidence}
            characters[]: {name, aliases[]}

            硬性约束：
            ① 只抽明确写出的，禁止脑补；
            ② 每条必带 evidence（原文原句），为空会被丢弃；
            ③ 优先高召回：拿不准的也抽出来，宁多勿漏——漏掉的设定会导致后续"吃书"事故，误抽由下游归并/待确认队列兜底；
            ④ entity 用角色/地点的标准名（首次出现用本名），attribute 用稳定可复用的措辞（如"惯用手"而非"怎么拿剑"）；
            ⑤ characters 只列「人物」角色（地点/门派/物品不进），name 填标准名/全名，aliases 填该角色的其他称呼（如"主角""林玄"）；settings 里的 entity 若指人物，也应与 characters.name 对齐。""";

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
                filterTimeline(ex.timeline()),
                filterCharacters(ex.characters()));
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
                // content 也必须非空：空 content 会落表但 embedding 空文本失败，导致「表有、向量无」不一致
                .filter(f -> isNotBlank(f.content()))
                .toList();
    }

    private static List<TimelineEvent> filterTimeline(List<TimelineEvent> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> isNotBlank(t.evidence()))
                .toList();
    }

    private static List<CharacterRef> filterCharacters(List<CharacterRef> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(c -> isNotBlank(c.name()))
                .toList();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
