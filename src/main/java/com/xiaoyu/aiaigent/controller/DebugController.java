package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.agent.IntentRouter;
import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import com.xiaoyu.aiaigent.mcp.SplitChapter;
import jakarta.annotation.Resource;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试/测试接口：意图路由测试 + MCP 工具真调测试（供前端「测试台」使用）。
 * workId 从登录态取，MCP 工具调用走连接池（懒连接，失败降级），不绕 MCP 直查 DB。
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    @Resource
    private IntentRouter intentRouter;

    @Resource
    private McpClientPool mcpClientPool;

    public record IntentRequest(String message) {
    }

    public record ParseRequest(String filePath) {
    }

    public record QueryRequest(String table, String fields, String conditions) {
    }

    public record SplitRequest(String text) {
    }

    public record EncodingRequest(String base64Content) {
    }

    public record ParseContentRequest(String fileName, String base64Content) {
    }

    /** 意图路由测试：输入一句话，返回路由出的意图 */
    @PostMapping("/intent")
    public Map<String, String> intent(@RequestBody IntentRequest req) {
        IntentRouter.RouteResult route = intentRouter.route(req.message());
        IntentRouter.Intent intent = route == null || route.intent() == null ? null : route.intent();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("intent", intent == null ? "CHAT" : intent.name());
        result.put("querySubType", route != null && route.querySubType() != null ? route.querySubType().name() : null);
        return result;
    }

    /** MCP 连通性探测：懒连接 doc-parser(8124) / database(8125) */
    @GetMapping("/mcp/status")
    public Map<String, Boolean> status() {
        TenantContext.Tenant t = TenantContext.get();
        boolean docParser = mcpClientPool.parseDocumentTool(t.userId(), t.workId()) != null;
        boolean database = mcpClientPool.queryDatabaseTool(t.userId(), t.workId()) != null;
        return Map.of("docParser", docParser, "database", database);
    }

    /** 真调 parse_document 工具（doc-parser MCP） */
    @PostMapping("/mcp/parse")
    public Map<String, String> parse(@RequestBody ParseRequest req) {
        ToolCallback tool = parseTool();
        if (tool == null) {
            return Map.of("error", "doc-parser MCP 未连接（8124）");
        }
        return Map.of("result", tool.call(ModelOptionsUtils.toJsonString(Map.of("filePath", req.filePath()))));
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

    /** 真调 split_chapters 工具（doc-parser MCP，整本切章） */
    @PostMapping("/mcp/split")
    public Map<String, Object> split(@RequestBody SplitRequest req) {
        TenantContext.Tenant t = TenantContext.get();
        List<SplitChapter> chapters = mcpClientPool.splitChapters(t.userId(), t.workId(), req.text());
        return Map.of("chapters", chapters);
    }

    /** 真调 detect_encoding 工具（doc-parser MCP，编码探测） */
    @PostMapping("/mcp/encoding")
    public Map<String, String> encoding(@RequestBody EncodingRequest req) {
        TenantContext.Tenant t = TenantContext.get();
        byte[] bytes = Base64.getDecoder().decode(req.base64Content());
        String enc = mcpClientPool.detectEncoding(t.userId(), t.workId(), bytes);
        return Map.of("encoding", enc == null ? "doc-parser MCP 未连接（8124）" : enc);
    }

    /** 真调 parse_document_content 工具（doc-parser MCP，内容解析） */
    @PostMapping("/mcp/parse-content")
    public Map<String, String> parseContent(@RequestBody ParseContentRequest req) {
        TenantContext.Tenant t = TenantContext.get();
        byte[] bytes = Base64.getDecoder().decode(req.base64Content());
        String text = mcpClientPool.parseDocumentContent(t.userId(), t.workId(), req.fileName(), bytes);
        return Map.of("result", text == null ? "doc-parser MCP 未连接（8124）" : text);
    }

    private ToolCallback parseTool() {
        TenantContext.Tenant t = TenantContext.get();
        return mcpClientPool.parseDocumentTool(t.userId(), t.workId());
    }

    private ToolCallback queryTool() {
        TenantContext.Tenant t = TenantContext.get();
        return mcpClientPool.queryDatabaseTool(t.userId(), t.workId());
    }
}
