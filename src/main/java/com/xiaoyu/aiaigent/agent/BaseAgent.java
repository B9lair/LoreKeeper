package com.xiaoyu.aiaigent.agent;


import com.xiaoyu.aiaigent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * 抽象基础代理类，用于管理代理状态和执行流程
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能
 * 子类必须实行step方法
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示
    private String systemPrompt;
    private String nextStepPrompt;

    // 状态（空闲）
    private AgentState state = AgentState.IDLE;

    // 执行控制
    private int maxSteps = 10;
    private int currentStep = 0;

    // LLM
    private ChatClient chatClient;

    // Memory(需要自主维护会话上下文)
    private List<Message> messageList = new ArrayList<>();

    // 最终回答（think 产生纯文本回答 / act 检测到 doTerminate 时由子类填充，供 runstream 发送 event:answer）
    private String lastAnswer;

    // 意图（由调用方在 runstream 前设置，供 runstream 发送 event:intent；可空）
    private String intent;

    // 会话记忆（可为空；为空则不启用多轮记忆）
    private ChatMemory chatMemory;

    // 会话 ID（多轮记忆按此隔离；为空则不启用）
    private String conversationId;

    // 记忆回看条数（历史最多注入多少条消息）
    private static final int MEMORY_RETRIEVE_SIZE = 10;

    /**
     * 运行代理
     * @param userPrompt 用户提示词
     * @return
     */
    public String run(String userPrompt){
        // 基础校验（不可执行）
        if(this.state != AgentState.IDLE){
            throw new RuntimeException("Cannot run agent from state:" + this.state);
        }
        if(StringUtil.isBlank(userPrompt)){
            throw new RuntimeException("Cannot run agent with empty prompt");
        }

        // 更改状态
        state = AgentState.RUNNING;

        // 记录消息上下文
        messageList.add(new UserMessage(userPrompt));

        // 定义保存结果列表
        List<String> results = new ArrayList<>();

        // 执行循环
        try {
            for(int i = 0; i < maxSteps && state != AgentState.FINISHED; i++){
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step" + stepNumber + "/" + maxSteps);
                // 单步执行
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if(currentStep >= maxSteps){
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();

        } finally {
            // 清理资源
            this.cleanup();
        }
    }

    /**
     * 运行代理（流式输出）
     * @param userPrompt 用户提示词
     * @return SseEmitter实例
     */
    public SseEmitter runstream(String userPrompt){

        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L);// 5分钟超时

        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() ->{

            try {

                // 基础校验（不可执行）
                if(this.state != AgentState.IDLE){
                    emitter.send(SseEmitter.event().name("error").data("错误：无法从状态运行代理" + this.state));
                    emitter.complete();
                    return;

                }
                if(StringUtil.isBlank(userPrompt)){
                    emitter.send(SseEmitter.event().name("error").data("错误：不能使用空提示词运行代理"));
                    emitter.complete();
                    return;
                }

                // 更改状态
                state = AgentState.RUNNING;
                // 记录消息上下文（先注入历史记忆，再追加本轮用户输入）
                injectHistory(userPrompt);

                // 发送意图事件（命名事件 intent，前端据此渲染意图徽章；未设置则跳过）
                if (!StringUtil.isBlank(intent)) {
                    emitter.send(SseEmitter.event().name("intent").data(intent));
                }

                // 执行循环
                try {
                    for(int i = 0; i < maxSteps && state != AgentState.FINISHED; i++){
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        log.info("Executing step" + stepNumber + "/" + maxSteps);

                        // 单步执行
                        String stepResult = step();
                        String result = "Step " + stepNumber + ": " + stepResult;

                        // 发送每一步的结果（命名事件 tool，前端据此渲染"过程区"）
                        emitter.send(SseEmitter.event().name("tool").data(result));

                    }
                    // 检查是否超出步骤限制
                    if(currentStep >= maxSteps){
                        state = AgentState.FINISHED;
                        emitter.send(SseEmitter.event().name("tool").data("执行结束：达到最大步骤(" + maxSteps + ")"));
                    }

                    // 发送最终回答（命名事件 answer，前端据此渲染"答案区"）
                    if(!StringUtil.isBlank(lastAnswer)){
                        emitter.send(SseEmitter.event().name("answer").data(lastAnswer));
                    }

                    // 正常完成
                    emitter.complete();

                } catch (Exception e) {
                    if (isClientDisconnected(e)) {
                        // 客户端主动断开（切会话 / 停止 / 刷新）是正常现象，静默处理，不刷 ERROR 日志
                        state = AgentState.FINISHED;
                        log.info("SSE 连接已由客户端断开，停止输出（conversationId={}）", conversationId);
                    } else {
                        state = AgentState.ERROR;
                        log.error("执行智能体失败", e);

                        try {
                            emitter.send(SseEmitter.event().name("error").data("执行错误：" + e.getMessage()));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    }

                } finally {
                    // 无论 SSE 连接是否异常（如客户端断开），都先持久化本轮对话，避免记忆丢失
                    persistTurn(userPrompt);
                    // 清理资源
                    this.cleanup();
                }

            } catch (Exception e) {
                emitter.completeWithError(e);
            }

        });

        // 设置超时回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        // 设置完成回调（修正：原先第二个 onTimeout 误写，应为 onCompletion）
        emitter.onCompletion(() -> {
            if(this.state == AgentState.RUNNING){
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.warn("SSE connection completed");
        });

        return emitter;
    }


    /**
     * 定义单个步骤
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup(){
        // 子类可以重写此方法来清理资源
    }

    /**
     * 注入历史对话记忆：把纯文本的用户/助手消息对拼到本轮用户输入之前。
     * 历史里只存「用户输入 + 最终回答」的纯文本对，不含工具中间消息。
     */
    private void injectHistory(String userPrompt) {
        if (chatMemory != null && !StringUtil.isBlank(conversationId)) {
            List<Message> history = chatMemory.get(conversationId, MEMORY_RETRIEVE_SIZE);
            if (!history.isEmpty()) {
                messageList.addAll(history);
                log.info("已注入历史记忆 {} 条（conversationId={}）", history.size(), conversationId);
            }
        }
        messageList.add(new UserMessage(userPrompt));
    }

    /**
     * 持久化本轮对话：只写入「用户输入 + 最终回答」这一对，
     * 工具调用的中间消息（ToolResponseMessage / 带 toolCalls 的 AssistantMessage）一律不落盘。
     */
    private void persistTurn(String userPrompt) {
        if (chatMemory == null || StringUtil.isBlank(conversationId) || StringUtil.isBlank(lastAnswer)) {
            return;
        }
        chatMemory.add(conversationId, List.of(
                new UserMessage(userPrompt),
                new AssistantMessage(lastAnswer)
        ));
        log.info("已持久化本轮对话（conversationId={}）", conversationId);
    }

    /**
     * 判断异常是否由「客户端主动断开 SSE 连接」引起（切会话/停止/刷新）。
     * 这种情况是正常交互，不应按系统错误处理。
     */
    private boolean isClientDisconnected(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("中止了一个已建立的连接")
                    || msg.contains("broken pipe")
                    || msg.contains("Connection reset"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

}
