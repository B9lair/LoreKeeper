package com.xiaoyu.aiaigent.agent;

import com.xiaoyu.aiaigent.advisor.MyLoggerAdvisor;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.ConsistencyReviewService;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import com.xiaoyu.aiaigent.rag.HybridSearchService;
import com.xiaoyu.aiaigent.repository.EvalTraceRepository;
import com.xiaoyu.aiaigent.tools.ConsistencyCheckTool;
import com.xiaoyu.aiaigent.tools.SettingSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 统一 Agent 工厂：一套工具 + 一套 SOP，由 LLM 自主规划工具调用（无意图路由）。
 */
@Component
public class LoreAgentFactory {

    private final ChatModel chatModel;

    private final HybridSearchService hybridSearchService;

    private final McpClientPool mcpClientPool;

    private final ChatMemory chatMemory;

    private final ToolCallback[] terminateTool;

    private final EvalTraceRepository evalTraceRepository;

    private final ConsistencyReviewService consistencyReviewService;

    /** 统一 Agent 的检索优先类型：优先召回设定/伏笔，不足补章节（无意图路由后的统一策略） */
    private static final Set<String> UNIFIED_PREFERRED_TYPES = Set.of("setting", "foreshadow");

    public LoreAgentFactory(ChatModel dashscopeChatModel,
                            HybridSearchService hybridSearchService,
                            McpClientPool mcpClientPool,
                            ChatMemory chatMemory,
                            ToolCallback[] terminateTool,
                            EvalTraceRepository evalTraceRepository,
                            ConsistencyReviewService consistencyReviewService) {
        this.chatModel = dashscopeChatModel;
        this.hybridSearchService = hybridSearchService;
        this.mcpClientPool = mcpClientPool;
        this.chatMemory = chatMemory;
        this.terminateTool = terminateTool;
        this.evalTraceRepository = evalTraceRepository;
        this.consistencyReviewService = consistencyReviewService;
    }

    /**
     * 统一 Agent 入口（无意图路由）：一套工具 + 一套 SOP，由 LLM 自主规划工具调用。
     * 工具集 = settingSearch + query_database + consistencyCheck + doTerminate。
     */
    public ToolCallAgent create(TenantContext.Tenant tenant, String conversationId) {
        long workId = tenant.workId();
        // 统一检索策略：优先召回设定/伏笔，不足补章节（不再按意图裁剪）
        ToolCallback settingSearch = ToolCallbacks.from(
                new SettingSearchTool(hybridSearchService, workId, null, UNIFIED_PREFERRED_TYPES))[0];
        // database MCP：结构化查询（伏笔状态等精确字段），失败降级为 null
        ToolCallback queryDatabase = mcpClientPool.queryDatabaseTool(tenant.userId(), workId);
        // 一致性检测工具：粘贴新章节正文做 dry-run 审查（只读，不落库）
        ToolCallback consistencyCheck = ToolCallbacks.from(
                new ConsistencyCheckTool(consistencyReviewService, workId))[0];

        ToolCallAgent agent = newAgent("LoreKeeper", AGENT_SOP,
                merge(settingSearch, terminateTool, queryDatabase, consistencyCheck));
        // 绑定会话记忆：conversationId 用于多轮记忆隔离（为空则不启用）
        agent.setConversationId(conversationId);
        agent.setChatMemory(chatMemory);
        // eval_trace 埋点：绑定 workId + repository（runstream 结束时落表）
        agent.setWorkId(workId);
        agent.setEvalTraceRepository(evalTraceRepository);
        return agent;
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

    /** settingSearch 放最前；mcpTools 可能含 null（MCP 未连接/降级），merge 时跳过 */
    private static ToolCallback[] merge(ToolCallback first, ToolCallback[] rest, ToolCallback... mcpTools) {
        List<ToolCallback> list = new ArrayList<>();
        list.add(first);
        for (ToolCallback mcpTool : mcpTools) {
            if (mcpTool != null) {
                list.add(mcpTool);
            }
        }
        list.addAll(Arrays.asList(rest));
        return list.toArray(new ToolCallback[0]);
    }

    /** 统一 Agent 的 SOP：无意图路由，一个 Agent 一套工具，由 LLM 自主规划 */
    static final String AGENT_SOP = """
        你是 LoreKeeper，网文创作设定管家。根据用户问题自主选择工具，完成任务后调用 doTerminate 结束。
        可用工具与用途：
        - settingSearch：检索作品设定库（角色/地点/规则/伏笔/已写章节），回答设定与世界观问题；
        - query_database：精确查询结构化数据（等值匹配，非语义检索），用于「查未回收伏笔(status=open)」「查时间线 timeline_event」「按实体名/属性精确查设定」；
        - consistencyCheck：设定一致性检测，用户要检查新写章节是否与既有设定矛盾（吃书）时调用，输入章节正文（只读，不写库）。
        规则：
        ① 优先基于 settingSearch 检索到的设定回答，并注明出处；只引用检索到的内容，禁止脑补新设定；
        ② 同一个 query 只检索一次，禁止重复检索；无论结果是否理想，都必须如实回答后调用 doTerminate 结束；
        ③ 若检索结果与问题主体不匹配（如问"女主"只检索到"女二"），必须如实说明"未找到相关设定"，禁止套用不匹配结果、禁止不输出文字就 doTerminate；
        ④ 未检索到相关设定时，如实说明"未检索到相关设定"，再 doTerminate。""";
}
