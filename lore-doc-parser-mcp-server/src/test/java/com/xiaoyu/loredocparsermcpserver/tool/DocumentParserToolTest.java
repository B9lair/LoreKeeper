package com.xiaoyu.loredocparsermcpserver.tool;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserToolTest {

    private final DocumentParserTool tool = new DocumentParserTool();

    // ---------- 原有 parse_document ----------
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

    // ---------- A：parse_document_content ----------
    @Test
    void parseDocumentContentTxt() {
        String text = "测试章节\n这是正文";
        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String result = tool.parseDocumentContent("chapter.txt", b64);
        assertTrue(result.contains("测试章节"), "应返回 TXT 纯文本");
    }

    @Test
    void parseDocumentContentUnsupported() {
        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        String result = tool.parseDocumentContent("a.xyz", b64);
        assertTrue(result.startsWith("不支持的类型"), "未知类型应提示");
    }

    // ---------- B：split_chapters ----------
    @Test
    void splitChaptersMulti() {
        String text = "第一章 青阳城\n正文一\n第二章 试剑\n正文二\n";
        String result = tool.splitChapters(text);
        assertTrue(result.contains("第一章 青阳城"), "应含第一章标题");
        assertTrue(result.contains("第二章 试剑"), "应含第二章标题");
        assertTrue(result.contains("正文一"), "应含第一章正文");
        assertTrue(result.contains("正文二"), "应含第二章正文");
    }

    @Test
    void splitChaptersNoHeading() {
        String result = tool.splitChapters("这是一段没有章节标题的全文");
        assertTrue(result.contains("全文"), "无标题应整体作为一章");
    }

    // ---------- B：detect_encoding ----------
    @Test
    void detectUtf8() {
        String b64 = Base64.getEncoder().encodeToString("测试".getBytes(StandardCharsets.UTF_8));
        assertTrue(tool.detectEncoding(b64).contains("UTF-8"), "应识别为 UTF-8");
    }

    @Test
    void detectGbk() {
        byte[] gbk = "测试".getBytes(Charset.forName("GBK"));
        String b64 = Base64.getEncoder().encodeToString(gbk);
        assertTrue(tool.detectEncoding(b64).contains("GBK"), "应识别为 GBK");
    }
}
