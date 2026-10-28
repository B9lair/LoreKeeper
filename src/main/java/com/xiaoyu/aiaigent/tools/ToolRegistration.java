package com.xiaoyu.aiaigent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册统一 Agent 的工具子集
 */
@Configuration
public class ToolRegistration {

    /** doTerminate：让自主规划智能体合理地中断 */
    @Bean
    public ToolCallback[] terminateTool() {
        return ToolCallbacks.from(new TerminateTool());
    }
}
