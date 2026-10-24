package com.xiaoyu.aiaigent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 意图路由器
 * 一次 LLM 结构化输出，把用户输入归入 5 类意图之一
 */
@Slf4j
@Component
public class IntentRouter {

    public enum Intent { WRITE, REVIEW, QUERY, FORESHADOW, CHAT }

    private final ChatClient chatClient;

    public IntentRouter(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    public Intent route(String message) {
        Intent intent = chatClient.prompt()
                .system("""
                        你是意图路由器，把用户输入归入下列之一：
                        WRITE 写新章节 / REVIEW 审查章节 / QUERY 查设定 / FORESHADOW 追踪伏笔 / CHAT 闲聊。
                        只输出枚举值，不要解释。""")
                .user(message)
                .call()
                .entity(Intent.class);
        log.info("路由意图：{}", intent);
        return intent;
    }
}
