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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * database MCP 客户端连接池（步骤 3.1）。
 *
 * 无状态 MCP + 连接级 JWT：每个 workId 一条带 {@code Authorization: Bearer <JWT>} 的连接。
 * 工具调用时由 {@link WorkIdBoundedMcpToolCallback} 把 workId 强制注入 arguments，
 * 服务端据此过滤，模型只能决定「查哪张表/哪几个字段/什么等值条件」，无法伪造 workId（2.7 隔离）。
 */
@Component
public class McpClientPool {

    private static final Logger log = LoggerFactory.getLogger(McpClientPool.class);

    private final JwtService jwtService;

    private final String databaseMcpUrl;

    /** key = workId，value = 该 workId 专属的 MCP 客户端 */
    private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();

    /** key = workId，value = 该 workId 专属的 query_database ToolCallback */
    private final Map<Long, ToolCallback> queryDatabaseTools = new ConcurrentHashMap<>();

    public McpClientPool(JwtService jwtService,
                         @Value("${lorekeeper.mcp.database-url:http://localhost:8125}") String databaseMcpUrl) {
        this.jwtService = jwtService;
        this.databaseMcpUrl = databaseMcpUrl;
    }

    /**
     * 获取某 tenant 专属的 query_database 工具（懒创建 + 按 workId 缓存）。
     * 连接失败或工具缺失时返回 null（降级：本次 Agent 不含该工具），不阻断其他功能。
     */
    public ToolCallback queryDatabaseTool(long userId, long workId) {
        ToolCallback cached = queryDatabaseTools.get(workId);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = queryDatabaseTools.get(workId);
            if (cached != null) {
                return cached;
            }
            try {
                String token = jwtService.issue(userId, workId);
                McpSyncClient client = createClient(token);
                ToolCallback tool = findQueryDatabase(client, workId);
                if (tool == null) {
                    client.close();
                    log.error("database MCP 未提供 query_database 工具，本次 Agent 将不含该工具");
                    return null;
                }
                clients.put(workId, client);
                queryDatabaseTools.put(workId, tool);
                return tool;
            }
            catch (Exception e) {
                log.error("连接 database MCP 失败，本次 Agent 将不含 query_database 工具: {}", e.getMessage());
                return null;
            }
        }
    }

    /** 创建带连接级 JWT 的 SSE 客户端并完成 MCP 初始化握手 */
    private McpSyncClient createClient(String token) {
        var transport = new BearerSseClientTransport(databaseMcpUrl, token);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
        return client;
    }

    /** 从 MCP 工具列表中找到 query_database，包装成绑定 workId 的回调 */
    private ToolCallback findQueryDatabase(McpSyncClient client, long workId) {
        Tool tool = client.listTools().tools().stream()
            .filter(t -> "query_database".equals(t.name()))
            .findFirst()
            .orElse(null);
        if (tool == null) {
            return null;
        }
        return new WorkIdBoundedMcpToolCallback(client, tool, workId);
    }

    @PreDestroy
    public void shutdown() {
        clients.forEach((workId, client) -> {
            try {
                client.close();
            }
            catch (Exception e) {
                log.warn("关闭 database MCP 客户端失败 workId={}: {}", workId, e.getMessage());
            }
        });
        clients.clear();
        queryDatabaseTools.clear();
    }
}
