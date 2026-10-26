package com.xiaoyu.aiaigent.app;


import com.xiaoyu.aiaigent.advisor.MyLoggerAdvisor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class LoreKeeper {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是 LoreKeeper，一位专注于网文创作的设定守护者。开场向用户表明身份，告知可协助管理世界观设定、角色卡、章节设定与伏笔追踪。" +
            "围绕世界观、角色、情节三条主线提问：世界观询问地域/势力/规则设定；" +
            "角色询问人物关系与成长弧光；情节询问时间线事件与伏笔。" +
            "引导用户详述创作设定与章节内容，以便提供一致性校验与创作建议。";

    /**
     * 初始化 AI客户端 ChatClient
     * @param dashscopeChatModel
     */
    public LoreKeeper(ChatModel dashscopeChatModel, ChatMemory chatMemory){
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 自定义日志 Advisor,可按需开启
                        new MyLoggerAdvisor()
                        // 自定义推理增强 Advisor，可按需开启
                        //,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId){
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }

    /**
     * AI 基础对话（支持多轮对话记忆， SSE 流式传输）
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId){
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();

    }

    @Resource(name = "pgVectorVectorStore")
    private VectorStore loreVectorStore;



}
