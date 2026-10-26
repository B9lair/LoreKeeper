package com.xiaoyu.aiaigent.lore;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 章节文件本地解析（TXT/MD/PDF/DOCX → 纯文本）。
 * 上传章节入库、一致性审查都是确定性动作（方案 2.4「何时用确定性编排」），
 * 不走 doc-parser MCP（Agent 工具，只收服务器本地路径），也避免核心链路依赖 8124 在线。
 */
@Service
public class FileParseService {

    public String parse(String fileName, byte[] content) {
        String ext = extension(fileName);
        return switch (ext) {
            case ".txt", ".md", ".markdown" -> new String(content, StandardCharsets.UTF_8);
            case ".pdf" -> parsePdf(content);
            case ".docx" -> parseDocx(content);
            default -> throw new IllegalArgumentException(
                    "不支持的文件类型：" + ext + "（仅支持 TXT / MD / PDF / DOCX）");
        };
    }

    private String extension(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i).toLowerCase();
    }

    private String parsePdf(byte[] content) {
        try (PDDocument doc = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(doc);
        } catch (Exception e) {
            throw new IllegalStateException("PDF 解析失败：" + e.getMessage(), e);
        }
    }

    private String parseDocx(byte[] content) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            throw new IllegalStateException("Word 解析失败：" + e.getMessage(), e);
        }
    }
}
