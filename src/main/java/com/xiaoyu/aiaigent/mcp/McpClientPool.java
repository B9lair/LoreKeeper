package com.xiaoyu.aiaigent.mcp;

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

/**
 * 自研 MCP 客户端连接池（database MCP）。
 *
 * 无状态 MCP + 连接级 JWT：每个 workId 一条带 {@code Authorization: Bearer <JWT>} 的连接。
 * 工具调用时由 {@link BoundedMcpToolCallback} 把可信键值（workId）强制注入 arguments，
 * 服务端据此过滤，模型只能决定业务字段，无法伪造隔离键。
 */
@Component
public class McpClientPool {

    private static final Logger log = LoggerFactory.getLogger(McpClientPool.class);

    private final JwtService jwtService;

    private final String databaseMcpUrl;

    /** key = workId，value = (工具名 -> 该 workId 专属 ToolCallback) */
    private final Map<Long, Map<String, ToolCallback>> toolCache = new ConcurrentHashMap<>();

    /** key = workId，value = 该 workId 的所有 MCP 连接（shutdown 用） */
    private final Map<Long, List<McpSyncClient>> clients = new ConcurrentHashMap<>();

    public McpClientPool(JwtService jwtService,
                         @Value("${lorekeeper.mcp.database-url:http://localhost:8125}") String databaseMcpUrl) {
        this.jwtService = jwtService;
        this.databaseMcpUrl = databaseMcpUrl;
    }

    /** 获取某 tenant 专属的 query_database 工具（3.1） */
    public ToolCallback queryDatabaseTool(long userId, long workId) {
        return tool(userId, workId, databaseMcpUrl, "query_database",
                Map.<String, Object>of("workId", workId));
    }

    /**
     * 懒创建 + 按 (workId, toolName) 缓存。连接失败或工具缺失时返回 null
     * （降级：本次 Agent 不含该工具），不阻断其他功能。
     */
    private ToolCallback tool(long userId, long workId, String baseUrl, String toolName,
                              Map<String, Object> injectedArgs) {
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
                ToolCallback callback = new BoundedMcpToolCallback(client, tool, injectedArgs);
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
