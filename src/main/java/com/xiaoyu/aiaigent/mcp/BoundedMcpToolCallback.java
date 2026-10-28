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
import java.util.function.Supplier;

/**
 * 绑定服务端注入键值（workId / 收件人邮箱等）的 MCP 工具回调（步骤 3.1 泛化，3.2 复用）。
 *
 * MCP 工具执行在异步线程，连接级 ThreadLocal（TenantContext）会跨线程丢失，
 * 故改为：调用前由本回调把可信键值（如 workId、收件人邮箱）强制注入 arguments，
 * 覆盖模型可能填入的值，模型无法伪造（方案 2.7 隔离 / 3.2 收件人）。
 */
public class BoundedMcpToolCallback implements ToolCallback {

    private final McpSyncClient mcpClient;

    private final Tool tool;

    /** 主项目（可信端）强制注入的静态键值，如 {workId=3}、{to=xxx@xx.com} */
    private final Map<String, Object> injectedArgs;

    /** 动态注入键值（每次调用时计算，用于把事实性内容从 LLM 手中收回，如邮件正文），可空 */
    private final Supplier<Map<String, Object>> dynamicArgs;

    /** 工具调用失败时的失效回调（连接失效后通知连接池清除缓存、下次重建），可空 */
    private final Runnable invalidateCallback;

    public BoundedMcpToolCallback(McpSyncClient mcpClient, Tool tool, Map<String, Object> injectedArgs) {
        this(mcpClient, tool, injectedArgs, null, null);
    }

    public BoundedMcpToolCallback(McpSyncClient mcpClient, Tool tool, Map<String, Object> injectedArgs,
                                  Supplier<Map<String, Object>> dynamicArgs) {
        this(mcpClient, tool, injectedArgs, dynamicArgs, null);
    }

    public BoundedMcpToolCallback(McpSyncClient mcpClient, Tool tool, Map<String, Object> injectedArgs,
                                  Supplier<Map<String, Object>> dynamicArgs, Runnable invalidateCallback) {
        this.mcpClient = mcpClient;
        this.tool = tool;
        this.injectedArgs = injectedArgs;
        this.dynamicArgs = dynamicArgs;
        this.invalidateCallback = invalidateCallback;
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
        arguments.putAll(injectedArgs); // 静态键值强制注入，覆盖模型可能填写的值
        if (dynamicArgs != null) {
            arguments.putAll(dynamicArgs.get()); // 动态键值最后注入，覆盖 LLM 填写的值
        }
        try {
            CallToolResult response = this.mcpClient.callTool(new CallToolRequest(this.tool.name(), arguments));
            return ModelOptionsUtils.toJsonString(response.content());
        }
        catch (Exception e) {
            // 连接失效（如 MCP Server 重启）等调用异常 → 触发失效回调，让连接池清除缓存、下次重建
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
            throw e;
        }
    }
}
