package com.xiaoyu.aiaigent.agent;

import com.xiaoyu.aiaigent.advisor.MyLoggerAdvisor;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.rag.HybridSearchService;
import com.xiaoyu.aiaigent.tools.SettingSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.stereotype.Component;

/**
 * 配置 Agent 工厂
 */
@Component
public class LoreAgentFactory {

    private final ChatModel chatModel;

    private final HybridSearchService hybridSearchService;

    private final ToolCallback[] writeTools;

    private final ToolCallback[] reviewTools;

    private final ToolCallback[] queryTools;

    private final ToolCallback[] foreshadowTools;

    public LoreAgentFactory(ChatModel dashscopeChatModel,
                            HybridSearchService hybridSearchService,
                            ToolCallback[] writeTools,
                            ToolCallback[] reviewTools,
                            ToolCallback[] queryTools,
                            ToolCallback[] foreshadowTools) {
        this.chatModel = dashscopeChatModel;
        this.hybridSearchService = hybridSearchService;
        this.writeTools = writeTools;
        this.reviewTools = reviewTools;
        this.queryTools = queryTools;
        this.foreshadowTools = foreshadowTools;
    }

    public ToolCallAgent create(IntentRouter.Intent intent, TenantContext.Tenant tenant) {
        long workId = tenant.workId();
        // workId 在此绑定进检索工具，模型只传 query，服务端注入 workId（2.7 隔离）
        ToolCallback settingSearch =
                ToolCallbacks.from(new SettingSearchTool(hybridSearchService, workId))[0];

        return switch (intent) {
            case WRITE       -> newAgent("设定提示", WRITE_SOP, merge(settingSearch, writeTools));
            case REVIEW      -> newAgent("一致性审查", REVIEW_SOP, merge(settingSearch, reviewTools));
            case QUERY       -> newAgent("世界观问答", QUERY_SOP, merge(settingSearch, queryTools));
            case FORESHADOW  -> newAgent("伏笔追踪", FORESHADOW_SOP, merge(settingSearch, foreshadowTools));
            default          -> newAgent("通用", CHAT_SOP, merge(settingSearch, queryTools));
        };
    }

    private ToolCallAgent newAgent(String name, String sop, ToolCallback[] tools) {
        ToolCallAgent agent = new ToolCallAgent(tools);
        agent.setName(name);
        agent.setSystemPrompt(sop);
        agent.setNextStepPrompt("根据需求自主选择工具，完成所有任务后调用 doTerminate 结束。");
        agent.setMaxSteps(20);
        agent.setChatClient(ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build());
        return agent;
    }

    /** settingSearch（动态绑定 workId）放最前，静态工具子集拼后面 */
    private static ToolCallback[] merge(ToolCallback first, ToolCallback[] rest) {
        ToolCallback[] all = new ToolCallback[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    static final String WRITE_SOP = """
        你是「设定提示」工作流。用户写新章节前，检索与当前剧情相关的已确立设定，生成设定提示卡。
        流程：① 调 settingSearch 检索相关设定 ② 按人物/地点/时间线/伏笔分类整理 ③ 输出提示卡。
        只引用检索到的设定，禁止脑补新设定。""";

    static final String REVIEW_SOP = """
        你是「一致性审查」工作流。用户提交新写的一章，逐条比对已有设定，输出矛盾清单。
        流程：① 解析章节（如给文件路径则调 parseDocument）② 抽取实体断言 ③ 调 settingSearch 检索该实体历史设定 ④ 逐条比对。
        每条矛盾必带原文证据与出处章节，禁止臆造。""";

    static final String QUERY_SOP = """
        你是「世界观问答」工作流。自然语言问设定，语义检索 + 结构化查询回答。
        流程：① 调 settingSearch 语义检索 ② 需精确字段（如伏笔状态）时调 queryDatabase ③ 综合回答并注明出处。""";

    static final String FORESHADOW_SOP = """
        你是「伏笔追踪」工作流。流程：① 调 queryDatabase 查未回收伏笔 ② 汇总状态 ③ 需要时调 sendEmail 提醒。""";

    static final String CHAT_SOP = """
        你是 LoreKeeper，面向网文作者的创作设定管家，回答创作相关问题。""";
}
