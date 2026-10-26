package com.xiaoyu.aiaigent.chatmemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证文件持久化对话记忆的读写往返（不依赖 DB / LLM）。
 * 核心风险点：Kryo 能否正确序列化/反序列化 Spring AI 的 UserMessage / AssistantMessage。
 */
class FileBasedChatMemoryTest {

    @Test
    void roundTrip_userAndAssistantMessages(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String convId = "test-conv";

        memory.add(convId, List.of(
                new UserMessage("第一轮用户问题"),
                new AssistantMessage("第一轮回答")
        ));

        List<Message> history = memory.get(convId, 10);
        assertEquals(2, history.size());
        // 反序列化后能还原文本内容
        assertEquals("第一轮用户问题", history.get(0).getText());
        assertEquals("第一轮回答", history.get(1).getText());
        // 类型也要还原（UserMessage / AssistantMessage）
        assertInstanceOf(UserMessage.class, history.get(0));
        assertInstanceOf(AssistantMessage.class, history.get(1));
    }

    @Test
    void get_lastN_returnsTail(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String convId = "test-conv";
        for (int i = 0; i < 6; i++) {
            memory.add(convId, List.of(new UserMessage("u" + i), new AssistantMessage("a" + i)));
        }

        List<Message> last4 = memory.get(convId, 4);
        assertEquals(4, last4.size());
        assertEquals("a5", last4.get(3).getText());
    }

    @Test
    void get_unknownConversation_returnsEmpty(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        assertTrue(memory.get("not-exist", 10).isEmpty());
    }

    @Test
    void clear_removesConversation(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        String convId = "test-conv";
        memory.add(convId, List.of(new UserMessage("hi"), new AssistantMessage("hello")));

        memory.clear(convId);

        assertTrue(memory.get(convId, 10).isEmpty());
    }
}
