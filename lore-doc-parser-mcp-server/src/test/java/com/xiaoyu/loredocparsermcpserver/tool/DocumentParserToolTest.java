package com.xiaoyu.loredocparsermcpserver.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserToolTest {

    private final DocumentParserTool tool = new DocumentParserTool();

    @Test
    void parseTxt() {
        String result = tool.parseDocument("src/test/resources/sample.txt");
        assertTrue(result.contains("测试章节"), "应返回 TXT 纯文本内容");
    }

    @Test
    void parseMarkdown() {
        String result = tool.parseDocument("src/test/resources/sample.md");
        assertTrue(result.contains("第一章"), "应返回 Markdown 纯文本内容");
    }

    @Test
    void unsupportedType() {
        String result = tool.parseDocument("a.xyz");
        assertTrue(result.startsWith("不支持的类型"), "未知扩展名应返回明确提示");
    }

    @Test
    void fileNotExists() {
        String result = tool.parseDocument("D:/not-exist-file.txt");
        assertTrue(result.startsWith("读取失败"), "文件不存在应返回错误提示而非抛异常");
    }
}
