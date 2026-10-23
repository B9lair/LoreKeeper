package com.xiaoyu.aiaigent.app;


import com.xiaoyu.aiaigent.advisor.MyLoggerAdvisor;
import com.xiaoyu.aiaigent.chatmemory.FileBasedChatMemory;
import com.xiaoyu.aiaigent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

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
    public LoreKeeper(ChatModel dashscopeChatModel){
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        // 初始化基于内存的对话记忆
        //ChatMemory chatMemory = new InMemoryChatMemory();

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

    record ConsistencyReport(String title, List<String> suggestions){

    }

    /**
     * AI 设定一致性审查功能（结构化输出，步骤 2.4 改造为 reviewChapter）
     * @param message
     * @param chatId
     * @return
     */
    public ConsistencyReport doChatWithReport(String message, String chatId){
        ConsistencyReport consistencyReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成设定审查结果，标题为{用户名}的设定报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(ConsistencyReport.class);

        log.info("consistencyReport:{}",consistencyReport);
        return consistencyReport;

    }


    // AI 设定库问答功能（RAG 检索使用 pgvector 持久化向量库，步骤 1.3）
    @Resource(name = "pgVectorVectorStore")
    private VectorStore loreVectorStore;

    @Resource
    private Advisor loreRagCloudAdvisor;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和RAG和知识库进行对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message,String chatId){
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewriter(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(loreVectorStore))
                // 应用 RAG 检索增强服务（云知识库服务）
//                .advisors(loreRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(loreVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoreRagCustomAdvisorFactory.createLoreRagCustomAdvisor(
//                                loreVectorStore,"单身"
//                        )
//                )
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }

    // AI 设定库调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 设定审查功能（支持调用工具）
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }



    // AI 调用 MCP 服务
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 设定审查功能（调用 MCP 服务）
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }



}
