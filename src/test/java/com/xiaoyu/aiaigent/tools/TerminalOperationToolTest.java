package com.xiaoyu.aiaigent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TerminalOperationToolTest {

    @Test
    void executeTerminalCommand() {
        TerminalOperationTool tool = new TerminalOperationTool();
        // 如果是 Windows 则使用 dir 替代 ls
        String command = System.getProperty("os.name").toLowerCase().contains("win") ? "dir" : "ls -l";
        String result = tool.executeTerminalCommand(command);
        assertNotNull(result);
    }

}