package com.xiaoyu.loredocparsermcpserver.tool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档解析工具集：
 * ① parse_document(filePath)         —— 本地路径解析（原契约，供 Agent 传服务器路径）
 * ② parse_document_content(fileName, base64) —— 内容解析（前端上传的二进制走 MCP）
 * ③ split_chapters(text)             —— 按章节标题切分（第X章 / Chapter X / 序章…）
 * ④ detect_encoding(base64)          —— 编码探测（UTF-8 / GBK）
 */
@Service
public class DocumentParserTool {

    /** 章节标题：中文「第X章/回/卷…」、英文「Chapter X」、序章/楔子/尾声/番外，仅匹配行首 */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "(?m)^[\\s　]*((?:第\\s*[0-9零一二三四五六七八九十百千万两]+\\s*[章节回卷部集篇]|Chapter\\s+\\d+|序章|楔子|尾声|番外).*)$");

    /** 支持的扩展名 */
    private static final Set<String> SUPPORTED = Set.of(".txt", ".md", ".markdown", ".pdf", ".docx");

    /** ① 解析本地文档（TXT/Markdown/PDF/Word），返回纯文本 */
    @Tool(name = "parse_document", description = "解析本地文档（TXT/Markdown/PDF/Word），返回纯文本")
    public String parseDocument(@ToolParam(description = "文件本地绝对路径") String filePath) {
        String ext = extension(filePath);
        if (!SUPPORTED.contains(ext)) {
            return unsupported(ext);
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            return parseBytes(ext, bytes);
        } catch (Exception e) {
            return "读取失败：" + e.getMessage();
        }
    }

    /** ② 解析文档内容（base64），供前端上传的二进制文件走 MCP 解析 */
    @Tool(name = "parse_document_content", description = "解析文档内容（base64），返回纯文本。适用于前端上传的文件")
    public String parseDocumentContent(
            @ToolParam(description = "文件名（含扩展名，用于判断类型）") String fileName,
            @ToolParam(description = "文件内容的 base64 编码") String base64Content) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Content);
            return parseBytes(extension(fileName), bytes);
        } catch (IllegalArgumentException e) {
            return "解析失败（base64 非法）：" + e.getMessage();
        }
    }

    /** ③ 章节切分：按章节标题把全文切分，返回 JSON 数组 [{title, content}] */
    @Tool(name = "split_chapters", description = "按「第X章/Chapter X」等标题把全文切分为章节，返回 JSON 数组 [{title, content}]")
    public String splitChapters(@ToolParam(description = "全文纯文本") String text) {
        if (text == null || text.isBlank()) {
            return "[]";
        }
        Matcher m = CHAPTER_PATTERN.matcher(text);
        List<String> titles = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            titles.add(m.group(1).trim());
            starts.add(m.start());
        }
        if (titles.isEmpty()) {
            return toJson(List.of(new Chapter("全文", text.trim())));
        }
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < titles.size()) ? starts.get(i + 1) : text.length();
            String body = text.substring(start, end);
            int lineEnd = body.indexOf('\n');
            String content = (lineEnd >= 0) ? body.substring(lineEnd + 1).trim() : "";
            chapters.add(new Chapter(titles.get(i), content));
        }
        return toJson(chapters);
    }

    /** ④ 编码探测：UTF-8 严格校验，失败判为 GBK */
    @Tool(name = "detect_encoding", description = "探测文本字节流编码（UTF-8 / GBK），返回编码名")
    public String detectEncoding(@ToolParam(description = "文本内容 base64") String base64Content) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Content);
            if (bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return "UTF-8 (BOM)";
            }
            return isValidUtf8(bytes) ? "UTF-8" : "GBK（或 GB18030）";
        } catch (Exception e) {
            return "探测失败：" + e.getMessage();
        }
    }

    // ---------- 私有：按扩展名 + bytes 分派解析 ----------

    private String parseBytes(String ext, byte[] bytes) {
        return switch (ext) {
            case ".txt", ".md", ".markdown" -> readText(bytes);
            case ".pdf" -> parsePdf(bytes);
            case ".docx" -> parseDocx(bytes);
            default -> unsupported(ext);
        };
    }

    private String unsupported(String ext) {
        return "不支持的类型：" + ext + "（仅支持 TXT/MD/PDF/DOCX）";
    }

    private String extension(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i).toLowerCase();
    }

    private String readText(byte[] bytes) {
        try {
            // 优先 UTF-8，非法则回退 GBK（解决中文 txt 乱码）
            String cs = isValidUtf8(bytes) ? StandardCharsets.UTF_8.name() : "GBK";
            return new String(bytes, Charset.forName(cs));
        } catch (Exception e) {
            return "读取失败：" + e.getMessage();
        }
    }

    private String parsePdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        } catch (Exception e) {
            return "PDF 解析失败：" + e.getMessage();
        }
    }

    private String parseDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            return "Word 解析失败：" + e.getMessage();
        }
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- JSON 序列化（手工，避免额外依赖） ----------

    public record Chapter(String title, String content) {}

    private String toJson(List<Chapter> chapters) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chapters.size(); i++) {
            if (i > 0) sb.append(',');
            Chapter c = chapters.get(i);
            sb.append("{\"title\":\"").append(escape(c.title()))
              .append("\",\"content\":\"").append(escape(c.content())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
