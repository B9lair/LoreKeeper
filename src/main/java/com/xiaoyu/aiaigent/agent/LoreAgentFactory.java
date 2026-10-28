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
 * 配置 Agent 工厂
 */
@Component
public class LoreAgentFactory {

    private final ChatModel chatModel;

    private final HybridSearchService hybridSearchService;

    private final McpClientPool mcpClientPool;

    private final ChatMemory chatMemory;

    private final ToolCallback[] writeTools;
    private final ToolCallback[] reviewTools;
    private final ToolCallback[] queryTools;
    private final ToolCallback[] foreshadowTools;

    private final EvalTraceRepository evalTraceRepository;

    private final ConsistencyReviewService consistencyReviewService;

    /** 统一 Agent 的检索优先类型：优先召回设定/伏笔，不足补章节（无意图路由后的统一策略） */
    private static final Set<String> UNIFIED_PREFERRED_TYPES = Set.of("setting", "foreshadow");

    public LoreAgentFactory(ChatModel dashscopeChatModel,
                            HybridSearchService hybridSearchService,
                            McpClientPool mcpClientPool,
                            ChatMemory chatMemory,
                            ToolCallback[] writeTools,
                            ToolCallback[] reviewTools,
                            ToolCallback[] queryTools,
                            ToolCallback[] foreshadowTools,
                            EvalTraceRepository evalTraceRepository,
                            ConsistencyReviewService consistencyReviewService) {
        this.chatModel = dashscopeChatModel;
        this.hybridSearchService = hybridSearchService;
        this.mcpClientPool = mcpClientPool;
        this.chatMemory = chatMemory;
        this.writeTools = writeTools;
        this.reviewTools = reviewTools;
        this.queryTools = queryTools;
        this.foreshadowTools = foreshadowTools;
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
                merge(settingSearch, queryTools, queryDatabase, consistencyCheck));
        // 绑定会话记忆：conversationId 用于多轮记忆隔离（为空则不启用）
        agent.setConversationId(conversationId);
        agent.setChatMemory(chatMemory);
        // eval_trace 埋点：绑定 workId + repository（runstream 结束时落表）
        agent.setWorkId(workId);
        agent.setEvalTraceRepository(evalTraceRepository);
        return agent;
    }

    public ToolCallAgent create(IntentRouter.Intent intent, TenantContext.Tenant tenant, String conversationId) {
        return create(intent, null, tenant, conversationId);
    }

    public ToolCallAgent create(IntentRouter.Intent intent, IntentRouter.QuerySubType querySubType,
                                TenantContext.Tenant tenant, String conversationId) {
        long workId = tenant.workId();
        // workId 在此绑定进检索工具，模型只传 query，服务端注入 workId（2.7 隔离）；
        // types 硬过滤（WRITE/REVIEW）与 preferredTypes 优先召回（QUERY_SETTING）按意图/子类型裁剪
        ToolCallback settingSearch = ToolCallbacks.from(
                new SettingSearchTool(hybridSearchService, workId, settingTypes(intent), preferredTypes(intent, querySubType)))[0];
        // database MCP：每 workId 一条带 JWT 的连接（连接级鉴权），从池取 query_database（3.1）
        ToolCallback queryDatabase = mcpClientPool.queryDatabaseTool(tenant.userId(), workId);
        // doc-parser（2.1）：解析 TXT/MD/PDF/Word 为纯文本，REVIEW 工作流按需调用（懒连接，失败降级为 null）
        ToolCallback parseDocument = mcpClientPool.parseDocumentTool(tenant.userId(), workId);

        ToolCallAgent agent = switch (intent) {
            case WRITE       -> newAgent("设定提示", WRITE_SOP, merge(settingSearch, writeTools));
            case REVIEW      -> newAgent("一致性审查", REVIEW_SOP, merge(settingSearch, reviewTools, parseDocument));
            case QUERY       -> newAgent("世界观问答", QUERY_SOP, merge(settingSearch, queryTools, queryDatabase));
            case FORESHADOW  -> newAgent("伏笔追踪", FORESHADOW_SOP, merge(settingSearch, foreshadowTools, queryDatabase));
            default          -> newAgent("通用", CHAT_SOP, merge(settingSearch, queryTools, queryDatabase));
        };
        // 绑定会话记忆：conversationId 用于多轮记忆隔离（为空则不启用）
        agent.setConversationId(conversationId);
        agent.setChatMemory(chatMemory);
        // eval_trace 埋点：绑定 workId + repository（阶段三 3.5，runstream 结束时落表）
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

    /** 各工作流检索的 type 白名单：提示卡聚焦设定+伏笔，审查聚焦设定，其余（问答/伏笔/闲聊）全量不过滤 */
    private static Set<String> settingTypes(IntentRouter.Intent intent) {
        return switch (intent) {
            case WRITE -> Set.of("setting", "foreshadow");
            case REVIEW -> Set.of("setting");
            default -> null;
        };
    }

    /** QUERY 子类型细分：仅「问设定」启用优先召回（优先设定/伏笔，不足补章节），其余（问剧情/降级/其他意图）全量 */
    static Set<String> preferredTypes(IntentRouter.Intent intent, IntentRouter.QuerySubType querySubType) {
        return (intent == IntentRouter.Intent.QUERY && querySubType == IntentRouter.QuerySubType.SETTING)
                ? Set.of("setting", "foreshadow")
                : null;
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

    static final String WRITE_SOP = """
        你是「设定提示」工作流。用户写新章节前，检索与当前剧情相关的已确立设定，生成设定提示卡。
        流程：① 调 settingSearch 检索相关设定 ② 按人物/地点/时间线/伏笔分类整理 ③ 输出提示卡。
        只引用检索到的设定，禁止脑补新设定。
        同一个 query 只检索一次，禁止重复检索；检索不到相关设定时，直接说明"未检索到相关设定"并基于已有信息给出提示，然后调用 doTerminate 结束。""";

    static final String REVIEW_SOP = """
        你是「一致性审查」工作流。用户提交新写的一章，逐条比对已有设定，输出矛盾清单。
        流程：① 解析章节（如给文件路径则调 parseDocument）② 抽取实体断言 ③ 调 settingSearch 检索该实体历史设定 ④ 逐条比对。
        每条矛盾必带原文证据与出处章节，禁止臆造。""";

    static final String QUERY_SOP = """
        你是「世界观问答」工作流。自然语言问设定，语义检索 + 结构化查询回答。
        流程：① 调 settingSearch 语义检索 ② 需精确字段（如伏笔状态）时调 queryDatabase ③ 综合回答并注明出处。
        同一个 query 只检索一次，禁止重复检索；无论检索结果是否理想，都必须基于已有结果如实回答，回答后立即调用 doTerminate 结束。
        若检索结果与问题主体不匹配（如用户问"女主"但只检索到"女二"），必须如实输出"未找到女主相关设定"这类结论，禁止把不匹配的结果套用到答案上，也禁止不输出任何文字就调用 doTerminate 结束。""";

    static final String FORESHADOW_SOP = """
        你是「伏笔追踪」工作流，负责查询并汇总未回收伏笔。
        必须按顺序执行，不可跳过：
        ① 调 queryDatabase 查询未回收伏笔（table=foreshadow，conditions=status=open）；
        ② 汇总未回收伏笔清单并输出；
        ③ 输出清单后，再调 doTerminate 结束。""";

    static final String CHAT_SOP = """
        你是 LoreKeeper，面向网文作者的创作设定管家，回答创作相关问题。""";

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
