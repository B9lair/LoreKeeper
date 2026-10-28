package com.xiaoyu.aiaigent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册工具子集
 */
@Configuration
public class ToolRegistration {

    /** 场景1 设定提示（写新章节）：仅 Terminate 占位（外部搜索/抓取工具已移除） */
    @Bean
    public ToolCallback[] writeTools() {
        return ToolCallbacks.from(new TerminateTool());
    }

    /** 场景2 一致性审查：仅 Terminate 占位（PDF 生成已移除，审查走确定性流水线） */
    @Bean
    public ToolCallback[] reviewTools() {
        return ToolCallbacks.from(new TerminateTool());
    }

    /** 场景3 世界观问答：仅 Terminate 占位（外部搜索/抓取工具已移除） */
    @Bean
    public ToolCallback[] queryTools() {
        return ToolCallbacks.from(new TerminateTool());
    }

    /** 场景4 伏笔追踪：queryDatabase(3.1) 由 MCP 池注入，这里只有 Terminate 占位 */
    @Bean
    public ToolCallback[] foreshadowTools() {
        return ToolCallbacks.from(new TerminateTool());
    }
}
