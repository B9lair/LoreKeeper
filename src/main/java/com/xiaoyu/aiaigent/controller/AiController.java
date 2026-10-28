package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.agent.LoreAgentFactory;
import com.xiaoyu.aiaigent.agent.ToolCallAgent;
import com.xiaoyu.aiaigent.common.TenantContext;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoreAgentFactory loreAgentFactory;

    /**
     * 统一入口：配置统一 Agent（无意图路由），SSE 流式返回。
     */
    @GetMapping("/lore/chat")
    public SseEmitter doChatWithLore(String message, String chatId) {
        TenantContext.Tenant tenant = TenantContext.get();   // 异步前显式取（ThreadLocal 不跨线程）
        // conversationId 加 userId 前缀，实现多租户会话隔离（chatId 为空则不启用记忆）
        // 分隔符用 "_" 而非 ":"：Windows 文件名不允许冒号，":" 会被当作 ADS 数据流导致文件写错
        String conversationId = (chatId == null || chatId.isBlank())
                ? null
                : tenant.userId() + "_" + chatId;
        // 统一 Agent：不再做意图路由，一套工具一套 SOP，由 LLM 自主规划
        ToolCallAgent agent = loreAgentFactory.create(tenant, conversationId);
        return agent.runstream(message);
    }

}
