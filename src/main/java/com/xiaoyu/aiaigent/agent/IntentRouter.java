package com.xiaoyu.aiaigent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 意图路由器
 * 一次 LLM 结构化输出，把用户输入归入 5 类意图之一，并在 QUERY 下细分「问设定/问剧情」子类型。
 */
@Slf4j
@Component
public class IntentRouter {

    public enum Intent { WRITE, REVIEW, QUERY, FORESHADOW, CHAT }

    /** QUERY 的子类型：SETTING 问设定/世界观事实，CHAPTER 问已写章节剧情；仅 QUERY 有意义 */
    public enum QuerySubType { SETTING, CHAPTER }

    /** 路由结果：一级意图 + 可选子类型（非 QUERY 时 querySubType 为 null） */
    public record RouteResult(Intent intent, QuerySubType querySubType) {}

    private final ChatClient chatClient;

    public IntentRouter(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    public RouteResult route(String message) {
        RouteResult result = chatClient.prompt()
                .system("""
                        你是意图路由器，把用户输入归入下列之一：
                        WRITE 写新章节 / REVIEW 审查章节 / QUERY 查设定 / FORESHADOW 追踪伏笔 / CHAT 闲聊。
                        当 intent 为 QUERY 时，额外输出 querySubType：
                          SETTING  问设定/角色/世界观事实（如"英莲的身世""男主的金手指"）
                          CHAPTER  问已写章节剧情（如"第3章发生了什么"）
                        非 QUERY 时 querySubType 置 null。
                        只输出 JSON：{"intent":"...","querySubType":"..."}，不要解释。""")
                .user(message)
                .call()
                .entity(RouteResult.class);
        // 路由降级兜底：解析失败或缺失一级意图时，安全侧归为 CHAT（不启用优先检索）
        if (result == null || result.intent() == null) {
            result = new RouteResult(Intent.CHAT, null);
        }
        log.info("路由意图：{} / 子类型：{}", result.intent(), result.querySubType());
        return result;
    }
}
