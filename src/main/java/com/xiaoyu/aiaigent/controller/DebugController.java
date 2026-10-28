package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import jakarta.annotation.Resource;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试/测试接口：MCP 工具真调测试（供前端「测试台」使用）。
 * workId 从登录态取，MCP 工具调用走连接池（懒连接，失败降级），不绕 MCP 直查 DB。
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    @Resource
    private McpClientPool mcpClientPool;

    public record QueryRequest(String table, String fields, String conditions) {
    }

    /** MCP 连通性探测：懒连接 database(8125) */
    @GetMapping("/mcp/status")
    public Map<String, Boolean> status() {
        TenantContext.Tenant t = TenantContext.get();
        boolean database = mcpClientPool.queryDatabaseTool(t.userId(), t.workId()) != null;
        return Map.of("database", database);
    }

    /** 真调 query_database 工具（database MCP） */
    @PostMapping("/mcp/query")
    public Map<String, String> query(@RequestBody QueryRequest req) {
        ToolCallback tool = queryTool();
        if (tool == null) {
            return Map.of("error", "database MCP 未连接（8125）");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("table", req.table());
        args.put("fields", req.fields() == null ? "" : req.fields());
        args.put("conditions", req.conditions() == null ? "" : req.conditions());
        return Map.of("result", tool.call(ModelOptionsUtils.toJsonString(args)));
    }

    private ToolCallback queryTool() {
        TenantContext.Tenant t = TenantContext.get();
        return mcpClientPool.queryDatabaseTool(t.userId(), t.workId());
    }
}
