package com.xiaoyu.aiaigent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册工具子集
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    /** 场景1 设定提示（写新章节）：检索设定 + 查外部灵感 */
    @Bean
    public ToolCallback[] writeTools() {
        return ToolCallbacks.from(
                new WebSearchTool(searchApiKey),
                new WebScrapingTool(),
                new TerminateTool());
    }

    /** 场景2 一致性审查：检索设定 + 导出 PDF（parse_document 在 2.4 确定性流水线里直接调） */
    @Bean
    public ToolCallback[] reviewTools() {
        return ToolCallbacks.from(
                new PDFGenerationTool(),
                new TerminateTool());
    }

    /** 场景3 世界观问答：检索设定 + 查外部资料 */
    @Bean
    public ToolCallback[] queryTools() {
        return ToolCallbacks.from(
                new WebSearchTool(searchApiKey),
                new WebScrapingTool(),
                new TerminateTool());
    }

    /** 场景4 伏笔追踪：queryDatabase(3.1)/sendEmail(3.2) 尚未实现，先只有 Terminate 占位 */
    @Bean
    public ToolCallback[] foreshadowTools() {
        return ToolCallbacks.from(new TerminateTool());
    }
}
