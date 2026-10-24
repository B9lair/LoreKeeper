package com.xiaoyu.loredocparsermcpserver.tool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档解析工具
 */
@Service
public class DocumentParserTool {

    // 通过 @Tool 暴露为 MCP 工具 parse_document
    @Tool(name = "parse_document", description = "解析创作文档（TXT/Markdown/PDF/Word），返回纯文本")
    public String parseDocument(@ToolParam(description = "文件本地路径（绝对路径），如 D:/novel/chapter1.txt") String filePath) {

        String ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase();
        return switch (ext) {
            case ".txt", ".md", ".markdown" -> readText(filePath);
            case ".pdf" -> parsePdf(filePath);
            case ".docx" -> parseDocx(filePath);
            default -> "不支持的类型：" + filePath;
        };
    }

    private String readText(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            return "读取失败：" + e.getMessage();
        }
    }

    private String parsePdf(String path) {
        try (var doc = Loader.loadPDF(Path.of(path).toFile())) {
            return new PDFTextStripper().getText(doc);
        } catch (Exception e) {
            return "PDF 解析失败：" + e.getMessage();
        }
    }

    private String parseDocx(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path));
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            return "Word 解析失败：" + e.getMessage();
        }
    }
}
