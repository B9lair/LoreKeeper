package com.xiaoyu.aiaigent.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定 workId 的 MCP 工具回调（步骤 3.1）。
 *
 * MCP 工具执行在异步线程，连接级 ThreadLocal（TenantContext）会跨线程丢失，
 * 故改为：调用前由本回调把 workId 强制注入 arguments（覆盖模型可能填入的值），
 * 服务端据此过滤，模型无法伪造 workId（方案 2.7 隔离）。
 */
public class WorkIdBoundedMcpToolCallback implements ToolCallback {

    private final McpSyncClient mcpClient;

    private final Tool tool;

    private final long workId;

    public WorkIdBoundedMcpToolCallback(McpSyncClient mcpClient, Tool tool, long workId) {
        this.mcpClient = mcpClient;
        this.tool = tool;
        this.workId = workId;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(this.tool.name())
            .description(this.tool.description())
            .inputSchema(ModelOptionsUtils.toJsonString(this.tool.inputSchema()))
            .build();
    }

    @Override
    public String call(String functionInput) {
        Map<String, Object> arguments = (functionInput == null || functionInput.isBlank())
                ? new LinkedHashMap<>()
                : ModelOptionsUtils.jsonToMap(functionInput);
        arguments.put("workId", workId); // 强制注入，覆盖模型可能填写的值
        CallToolResult response = this.mcpClient.callTool(new CallToolRequest(this.tool.name(), arguments));
        return ModelOptionsUtils.toJsonString(response.content());
    }
}
