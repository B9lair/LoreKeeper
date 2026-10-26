package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.chatmemory.FileBasedChatMemory;
import com.xiaoyu.aiaigent.common.TenantContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话管理：列出历史会话、读取会话消息、删除会话。
 * 会话按用户隔离（conversationId = userId_chatId，分隔符用下划线规避 Windows 文件名冒号非法），chatId 由前端生成。
 */
@RestController
@RequestMapping("/chat")
public class ConversationController {

    private final FileBasedChatMemory chatMemory;

    public ConversationController(FileBasedChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /** 会话消息（只回放文本内容，工具过程不回放） */
    public record MessageDto(String role, String content) {
    }

    /** 会话摘要（列表项） */
    public record ConversationSummary(String chatId, long updatedAt, int messageCount, String preview) {
    }

    /** 列出当前用户的历史会话（按最近更新倒序） */
    @GetMapping("/conversations")
    public List<ConversationSummary> list() {
        long userId = TenantContext.get().userId();
        String prefix = userId + "_";
        return chatMemory.listConversations().stream()
                .filter(info -> info.conversationId().startsWith(prefix))
                .map(info -> toSummary(info, prefix))
                .toList();
    }

    /** 读取某会话的完整历史消息 */
    @GetMapping("/conversations/{chatId}")
    public List<MessageDto> messages(@PathVariable String chatId) {
        return chatMemory.getAllMessages(convIdOf(chatId)).stream()
                .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
                .map(m -> new MessageDto(roleOf(m), m.getText()))
                .toList();
    }

    /** 删除某会话 */
    @DeleteMapping("/conversations/{chatId}")
    public void delete(@PathVariable String chatId) {
        chatMemory.clear(convIdOf(chatId));
    }

    private String convIdOf(String chatId) {
        return TenantContext.get().userId() + "_" + chatId;
    }

    private ConversationSummary toSummary(FileBasedChatMemory.ConversationInfo info, String prefix) {
        String chatId = info.conversationId().substring(prefix.length());
        List<Message> msgs = chatMemory.getAllMessages(info.conversationId());
        String preview = msgs.stream()
                .filter(m -> m instanceof UserMessage)
                .map(Message::getText)
                .findFirst()
                .orElse("");
        if (preview.length() > 30) {
            preview = preview.substring(0, 30) + "…";
        }
        return new ConversationSummary(chatId, info.lastModified(), msgs.size(), preview);
    }

    private String roleOf(Message m) {
        if (m instanceof UserMessage) return "user";
        if (m instanceof AssistantMessage) return "assistant";
        return "system";
    }
}
