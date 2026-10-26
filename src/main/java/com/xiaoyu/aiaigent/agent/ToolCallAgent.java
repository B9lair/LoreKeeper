package com.xiaoyu.aiaigent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;

import com.xiaoyu.aiaigent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 * (手动控制工具执行)
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent{

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存了工具调用信息的响应
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用内置的工具调用机制，自己维护上下文
    private final ChatOptions chatOptions;

    // 防死循环：记录上一次工具调用签名与连续重复次数，检测到重复调用时强制收敛
    private static final int REPEATED_CALL_LIMIT = 3;
    private String lastToolSignature;
    private int repeatedCallCount = 0;
    private boolean loopGuardTriggered = false;

    public ToolCallAgent(ToolCallback[] availableTools){
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {

        try {

            // 1.校验提示词，拼接用户提示词
            if(StrUtil.isNotBlank(getNextStepPrompt())){
                UserMessage userMessage = new UserMessage(getNextStepPrompt());
                getMessageList().add(userMessage);
            }

            // 2.调用 AI 大模型，获取工具调用结果
            List<Message> messageList = getMessageList();
            Prompt prompt = new Prompt(messageList, this.chatOptions);
            // 获取带工具选项的响应
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于 Act
            this.toolCallChatResponse = chatResponse;

            // 3.解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 输出提示信息
            String result = assistantMessage.getText();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s"
                            ,toolCall.name()
                            ,toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            if(toolCallList.isEmpty()){
                // 只有不调用工具时，才记录助手消息
                getMessageList().add(assistantMessage);
                // 记录最终回答（无工具调用的纯文本回答，供 runstream 发送 event:answer）
                setLastAnswer(result);
                // LLM 不再调用工具 = 任务完成，主动结束（与 doTerminate 等价）
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                return true;
            }

        } catch (Exception e) {

            // 4.异常处理
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误：" + e.getMessage()));
            return false;

        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if(!toolCallChatResponse.hasToolCalls()){
            return "没有工具调用";
        }

        // 防死循环：统计连续重复调用（相同工具 + 相同参数），命中阈值时强制收敛
        String signature = toolCallChatResponse.getResult().getOutput().getToolCalls().stream()
                .map(toolCall -> toolCall.name() + "::" + toolCall.arguments())
                .collect(Collectors.joining("||"));

        if (signature.equals(lastToolSignature)) {
            repeatedCallCount++;
        } else {
            repeatedCallCount = 1;
            lastToolSignature = signature;
        }

        if (repeatedCallCount >= REPEATED_CALL_LIMIT) {
            if (!loopGuardTriggered) {
                // 首次触发：注入终止指令，给模型一次收敛机会（下一轮应直接回答或调 doTerminate）
                loopGuardTriggered = true;
                getMessageList().add(new UserMessage(
                        "检测到你已连续 " + repeatedCallCount + " 次用相同参数调用同一工具，疑似陷入循环。"
                        + "请停止重复检索，直接基于已有信息给出最终回答，并调用 doTerminate 结束。"));
                log.warn("{} 连续重复调用工具，已注入终止指令：{}", getName(), signature);
                return "检测到重复工具调用，已注入终止指令，等待模型收敛";
            } else {
                // 二次触发：模型仍未收敛，强制结束，避免持续烧 token
                setLastAnswer("（检索结果不足以直接回答该问题，请换一个更具体的关键词再试。）");
                setState(AgentState.FINISHED);
                log.warn("{} 注入终止指令后仍未收敛，已强制结束", getName());
                return "重复循环未收敛，已强制结束";
            }
        }

        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文,conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        // 当前工具调用的结果
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + "完成了它的任务！结果：" + response.responseData())
                .collect(Collectors.joining("\n"));

        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                        .anyMatch(response -> "doTerminate".equals(response.name()));
        if(terminateToolCalled){
            // 任务结束，记录最终回答（doTerminate 那轮的文本回答），再更改状态
            AssistantMessage lastAssistantMessage = toolCallChatResponse.getResult().getOutput();
            String finalText = lastAssistantMessage.getText();
            if (StrUtil.isNotBlank(finalText)) {
                setLastAnswer(finalText);
            }
            setState(AgentState.FINISHED);
        }

        log.info(results);
        return results;

    }


}
