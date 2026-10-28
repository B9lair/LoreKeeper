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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


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

    // 本轮执行开始时间戳（毫秒，run/runstream 开头记录；埋点算耗时用，getElapsedMs 读取）
    private long startedAt;

    // LLM
    private ChatClient chatClient;

    // Memory(需要自主维护会话上下文)
    private List<Message> messageList = new ArrayList<>();

    // 最终回答（think 产生纯文本回答 / act 检测到 doTerminate 时由子类填充，供 runstream 发送 event:answer）
    private String lastAnswer;

    // 会话记忆（可为空；为空则不启用多轮记忆）
    private ChatMemory chatMemory;

    // 会话 ID（多轮记忆按此隔离；为空则不启用）
    private String conversationId;

    // 租户 workId（eval_trace 落库用，LoreAgentFactory.create 时注入；可为空）
    private Long workId;

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

        // 记录开始时间（埋点：端到端耗时）
        this.startedAt = System.currentTimeMillis();

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

        // 客户端断连标记：safeSend 返回 false 或 onError 回调触发时置 true。
        // 断连后不能再调用 emitter.complete()/send()，否则 AsyncContext 已进入错误态会抛 IllegalStateException
        // 并级联触发 /error 分发（表现为 AuthorizationDeniedException 噪音）。
        AtomicBoolean disconnected = new AtomicBoolean(false);

        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() ->{

            try {

                // 基础校验（不可执行）
                if(this.state != AgentState.IDLE){
                    safeSend(emitter, "error", "错误：无法从状态运行代理" + this.state);
                    emitter.complete();
                    return;

                }
                if(StringUtil.isBlank(userPrompt)){
                    safeSend(emitter, "error", "错误：不能使用空提示词运行代理");
                    emitter.complete();
                    return;
                }

                // 更改状态
                state = AgentState.RUNNING;
                // 记录开始时间（埋点：端到端耗时）
                this.startedAt = System.currentTimeMillis();
                // 记录消息上下文（先注入历史记忆，再追加本轮用户输入）
                injectHistory(userPrompt);

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
                        if (!safeSend(emitter, "tool", result)) {
                            disconnected.set(true);
                            state = AgentState.FINISHED;
                            break;
                        }

                    }
                    // 检查是否超出步骤限制
                    if(!disconnected.get() && currentStep >= maxSteps && state != AgentState.FINISHED){
                        state = AgentState.FINISHED;
                        if (!safeSend(emitter, "tool", "执行结束：达到最大步骤(" + maxSteps + ")")) {
                            disconnected.set(true);
                        }
                    }

                    // 发送最终回答（命名事件 answer，前端据此渲染"答案区"）
                    if(!disconnected.get() && !StringUtil.isBlank(lastAnswer)){
                        if (!safeSend(emitter, "answer", lastAnswer)) {
                            disconnected.set(true);
                        }
                    }

                    // 正常完成（客户端断连后不能再 complete，否则 AsyncContext 已进入错误态会抛 IllegalStateException）
                    if(!disconnected.get()){
                        completeQuietly(emitter);
                    }

                } catch (Exception e) {
                    state = AgentState.ERROR;
                    if (disconnected.get()) {
                        // 客户端已断开，异常属预期（如最后时刻断连导致的 complete 失败），降级 INFO 不再打 ERROR
                        log.info("SSE 客户端已断开，忽略执行异常（conversationId={}）：{}", conversationId, e.getMessage());
                    } else {
                        log.error("执行智能体失败", e);
                        try {
                            emitter.send(SseEmitter.event().name("error").data("执行错误：" + e.getMessage()));
                            completeQuietly(emitter);
                        } catch (Exception ex) {
                            completeWithErrorQuietly(emitter, ex);
                        }
                    }

                } finally {
                    // 无论 SSE 连接是否异常（如客户端断开），都先持久化本轮对话，避免记忆丢失
                    persistTurn(userPrompt);
                    // 埋点落库（eval_trace，阶段三 3.5）：子类 ToolCallAgent 实现，记录意图/工具序列/回答/loopGuard/步数/耗时
                    onFinish(userPrompt);
                    // 清理资源
                    this.cleanup();
                }

            } catch (Exception e) {
                completeWithErrorQuietly(emitter, e);
            }

        });

        // 设置超时回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        // 客户端断连 / 异步错误回调：置断连标记，阻止后续 complete/send 触发 AsyncContext 非法访问。
        // 日志降级 DEBUG：safeSend 已打印「客户端断开」、onCompletion 已打印「completed」，
        // 此处 INFO 会与 safeSend 重复（断连时连打 3 条 INFO 噪音），故仅 debug 保留排查线索。
        emitter.onError((e) -> {
            disconnected.set(true);
            log.debug("SSE 连接错误（客户端可能已断开，conversationId={}）：{}",
                    conversationId, e == null ? "null" : e.getMessage());
        });

        // 设置完成回调（修正：原先第二个 onTimeout 误写，应为 onCompletion）
        emitter.onCompletion(() -> {
            disconnected.set(true);
            if(this.state == AgentState.RUNNING){
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }


    /**
     * 定义单个步骤
     * @return
     */
    public abstract String step();

    /**
     * 本轮端到端耗时（毫秒）。run/runstream 尚未开始时返回 0。
     */
    public long getElapsedMs() {
        return startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt;
    }

    /**
     * 执行结束回调（供子类埋点，如 ToolCallAgent 落 eval_trace）。
     * 仅在 runstream 的 finally（persistTurn 之后、cleanup 之前）调用；
     * run() 同步路径不调用，避免评测运行器污染线上 trace。
     */
    protected void onFinish(String userPrompt) {
        // 默认空实现
    }

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
     * SSE 安全发送：在 send 调用处就地拦截 IOException（客户端断连），
     * 阻止异常传播到 Spring DispatcherServlet 触发 ERROR 日志。
     *
     * @return true 发送成功，false 客户端已断连（调用方应停止后续发送）
     */
    private boolean safeSend(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException e) {
            log.info("SSE 连接已由客户端断开（发送 {} 时），停止输出（conversationId={}）", eventName, conversationId);
            return false;
        }
    }

    /**
     * SSE 静默完成：客户端断连后 AsyncContext 已进入错误态，此时调用 emitter.complete()
     * 会抛 IllegalStateException 并级联触发 /error 分发（表现为 AuthorizationDeniedException 噪音）。
     * 这里把这类「最后时刻断连」的竞态一并吞掉，避免噪音。
     */
    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.info("SSE 连接在完成前已断开，忽略 complete 异常（conversationId={}）", conversationId);
        }
    }

    /**
     * SSE 静默以错误完成：仅在仍有意义的错误场景调用；客户端已断连时直接忽略。
     */
    private void completeWithErrorQuietly(SseEmitter emitter, Throwable t) {
        try {
            emitter.completeWithError(t);
        } catch (Exception ignored) {
            log.debug("SSE completeWithError 失败（客户端可能已断连），忽略：{}", ignored.getMessage());
        }
    }

}
