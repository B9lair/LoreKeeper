package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.agent.IntentRouter;
import com.xiaoyu.aiaigent.agent.LoreAgentFactory;
import com.xiaoyu.aiaigent.agent.ToolCallAgent;
import com.xiaoyu.aiaigent.app.LoreKeeper;
import com.xiaoyu.aiaigent.common.TenantContext;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import com.xiaoyu.aiaigent.lore.ConsistencyReport;
import com.xiaoyu.aiaigent.lore.ConsistencyReviewService;
import com.xiaoyu.aiaigent.lore.FileParseService;
import com.xiaoyu.aiaigent.lore.ReviewRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoreKeeper loreKeeper;

    @Resource
    private IntentRouter intentRouter;

    @Resource
    private LoreAgentFactory loreAgentFactory;

    @Resource
    private ConsistencyReviewService consistencyReviewService;

    @Resource
    private FileParseService fileParseService;


    @GetMapping("/lore/chat/sync")
    public String doChatWithLoreSync(String message, String chatId) {
        return loreKeeper.doChat(message, chatId);
    }

    @GetMapping(value = "/lore/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoreSSE(String message, String chatId) {
        return loreKeeper.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/lore/chat/sse")
    public Flux<ServerSentEvent<String>> doChatWithLoreServerSentEvent(String message, String chatId) {
        return loreKeeper.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    @GetMapping("/lore/chat/sse/emitter")
    public SseEmitter doChatWithLoreSseEmitter(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(180000L);
        loreKeeper.doChatByStream(message, chatId)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    /**
     * 统一入口：意图路由 + 配置 Agent
     */
    @GetMapping("/lore/chat")
    public SseEmitter doChatWithLore(String message, String chatId) {
        TenantContext.Tenant tenant = TenantContext.get();   // 异步前显式取（ThreadLocal 不跨线程）
        IntentRouter.RouteResult route = intentRouter.route(message);
        IntentRouter.Intent intent = route.intent();
        IntentRouter.QuerySubType querySubType = route.querySubType();
        // conversationId 加 userId 前缀，实现多租户会话隔离（chatId 为空则不启用记忆）
        // 分隔符用 "_" 而非 ":"：Windows 文件名不允许冒号，":" 会被当作 ADS 数据流导致文件写错
        String conversationId = (chatId == null || chatId.isBlank())
                ? null
                : tenant.userId() + "_" + chatId;
        ToolCallAgent agent = loreAgentFactory.create(intent, querySubType, tenant, conversationId);
        agent.setIntent(intent.name());   // 前端据此渲染意图徽章（event:intent）
        return agent.runstream(message);
    }

    /**
     * 一致性审查（文本版）
     * workId 从登录态取，不信任请求参数。
     */
    @PostMapping("/lore/review")
    public ConsistencyReport reviewChapter(@RequestBody ReviewRequest req) {
        long workId = TenantContext.get().workId();
        return consistencyReviewService.reviewChapter(workId, req.chapterNo(), req.chapterText());
    }

    /**
     * 一致性审查（文件上传版）：前端上传 TXT/MD/PDF/Word，主项目本地解析为纯文本后走确定性审查流水线。
     * 不经过 Agent / doc-parser MCP（上传文件是确定性动作、审查是确定性流水线，见方案 2.4）。
     */
    @PostMapping("/lore/review/file")
    public ConsistencyReport reviewFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "chapterNo", required = false) Integer chapterNo) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String text;
        try {
            text = fileParseService.parse(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败：" + e.getMessage(), e);
        }
        long workId = TenantContext.get().workId();
        return consistencyReviewService.reviewChapter(workId, chapterNo, text);
    }

}
