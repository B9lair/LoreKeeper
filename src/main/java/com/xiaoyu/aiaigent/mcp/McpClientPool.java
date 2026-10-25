package com.xiaoyu.aiaigent.mcp;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.security.JwtService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 自研 MCP 客户端连接池（database 3.1 / notify 3.2）。
 *
 * 无状态 MCP + 连接级 JWT：每个 workId 一条带 {@code Authorization: Bearer <JWT>} 的连接。
 * 工具调用时由 {@link BoundedMcpToolCallback} 把可信键值（workId / 收件人邮箱）强制注入 arguments，
 * 服务端据此过滤/定收件人，模型只能决定业务字段，无法伪造隔离键（2.7 / 4.3）。
 */
@Component
public class McpClientPool {

    private static final Logger log = LoggerFactory.getLogger(McpClientPool.class);

    private final JwtService jwtService;

    private final ForeshadowRepository foreshadowRepository;

    private final String databaseMcpUrl;

    private final String notifyMcpUrl;

    /** key = workId，value = (工具名 -> 该 workId 专属 ToolCallback) */
    private final Map<Long, Map<String, ToolCallback>> toolCache = new ConcurrentHashMap<>();

    /** key = workId，value = 该 workId 的所有 MCP 连接（shutdown 用） */
    private final Map<Long, List<McpSyncClient>> clients = new ConcurrentHashMap<>();

    public McpClientPool(JwtService jwtService,
                         ForeshadowRepository foreshadowRepository,
                         @Value("${lorekeeper.mcp.database-url:http://localhost:8125}") String databaseMcpUrl,
                         @Value("${lorekeeper.mcp.notify-url:http://localhost:8126}") String notifyMcpUrl) {
        this.jwtService = jwtService;
        this.foreshadowRepository = foreshadowRepository;
        this.databaseMcpUrl = databaseMcpUrl;
        this.notifyMcpUrl = notifyMcpUrl;
    }

    /** 获取某 tenant 专属的 query_database 工具（3.1） */
    public ToolCallback queryDatabaseTool(long userId, long workId) {
        return tool(userId, workId, databaseMcpUrl, "query_database",
                Map.<String, Object>of("workId", workId), null);
    }

    /** 获取某 tenant 专属的 send_email 工具：收件人静态注入，正文动态注入（真实伏笔清单，3.2） */
    public ToolCallback sendEmailTool(long userId, long workId, String email) {
        // 静态注入 to；动态注入 subject/body（查真实 open 伏笔拼正文，从 LLM 手中收回，杜绝编造）
        Supplier<Map<String, Object>> dynamicArgs = () -> Map.of(
                "subject", "连载更新提醒",
                "body", buildForeshadowReminderBody(workId));
        return tool(userId, workId, notifyMcpUrl, "send_email",
                Map.<String, Object>of("to", email), dynamicArgs);
    }

    /**
     * 懒创建 + 按 (workId, toolName) 缓存。连接失败或工具缺失时返回 null
     * （降级：本次 Agent 不含该工具），不阻断其他功能。
     */
    private ToolCallback tool(long userId, long workId, String baseUrl, String toolName,
                              Map<String, Object> injectedArgs, Supplier<Map<String, Object>> dynamicArgs) {
        Map<String, ToolCallback> byTool = toolCache.get(workId);
        if (byTool != null && byTool.containsKey(toolName)) {
            return byTool.get(toolName);
        }
        synchronized (this) {
            byTool = toolCache.get(workId);
            if (byTool != null && byTool.containsKey(toolName)) {
                return byTool.get(toolName);
            }
            try {
                String token = jwtService.issue(userId, workId);
                McpSyncClient client = createClient(baseUrl, token);
                Tool tool = findTool(client, toolName);
                if (tool == null) {
                    client.close();
                    log.error("MCP {} 未提供 {} 工具，本次 Agent 将不含该工具", baseUrl, toolName);
                    return null;
                }
                clients.computeIfAbsent(workId, k -> new CopyOnWriteArrayList<>()).add(client);
                ToolCallback callback = new BoundedMcpToolCallback(client, tool, injectedArgs, dynamicArgs);
                toolCache.computeIfAbsent(workId, k -> new ConcurrentHashMap<>()).put(toolName, callback);
                return callback;
            }
            catch (Exception e) {
                log.error("连接 MCP {} 失败，本次 Agent 将不含 {} 工具: {}", baseUrl, toolName, e.getMessage());
                return null;
            }
        }
    }

    /** 创建带连接级 JWT 的 SSE 客户端并完成 MCP 初始化握手 */
    private McpSyncClient createClient(String baseUrl, String token) {
        var transport = new BearerSseClientTransport(baseUrl, token);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
        return client;
    }

    /** 从 MCP 工具列表中找到指定工具 */
    private Tool findTool(McpSyncClient client, String toolName) {
        return client.listTools().tools().stream()
            .filter(t -> toolName.equals(t.name()))
            .findFirst()
            .orElse(null);
    }

    /** 拼装伏笔提醒正文：用代码查真实 open 伏笔生成，不从 LLM 转述（事实性内容交给代码） */
    private String buildForeshadowReminderBody(long workId) {
        List<String> contents = foreshadowRepository.findOpenContentsByWorkId(workId);
        if (contents.isEmpty()) {
            return "您当前没有未回收的伏笔。";
        }
        StringBuilder sb = new StringBuilder("您有以下伏笔待回收：\n\n");
        for (int i = 0; i < contents.size(); i++) {
            sb.append(i + 1).append(". ").append(contents.get(i)).append("\n");
        }
        sb.append("\n请尽快处理。");
        return sb.toString();
    }

    @PreDestroy
    public void shutdown() {
        clients.forEach((workId, list) -> list.forEach(client -> {
            try {
                client.close();
            }
            catch (Exception e) {
                log.warn("关闭 MCP 客户端失败 workId={}: {}", workId, e.getMessage());
            }
        }));
        clients.clear();
        toolCache.clear();
    }
}
