package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 章节文件解析（TXT/MD/PDF/DOCX → 纯文本）。
 * 上传章节入库、一致性审查都是确定性动作（方案 2.4「何时用确定性编排」）。
 * P1：优先走 doc-parser 的 parse_document_content（统一解析 + UTF-8/GBK 编码回退），
 * 8124 不在线/失败时降级本地解析，核心链路不强依赖 MCP。
 */
@Service
public class FileParseService {

    private final McpClientPool mcpClientPool;

    public FileParseService(McpClientPool mcpClientPool) {
        this.mcpClientPool = mcpClientPool;
    }

    public String parse(String fileName, byte[] content) {
        // 优先走 doc-parser（消除重复解析依赖 + 编码回退），失败/离线降级本地
        try {
            TenantContext.Tenant tenant = TenantContext.get();
            String text = mcpClientPool.parseDocumentContent(tenant.userId(), tenant.workId(), fileName, content);
            if (text != null && !isError(text)) {
                return text;
            }
        } catch (Exception ignored) {
            // TenantContext 未初始化（非请求线程/测试）或 MCP 异常：降级本地解析
        }
        return parseLocal(fileName, content);
    }

    private boolean isError(String text) {
        return text.startsWith("不支持的类型") || text.startsWith("解析失败")
                || text.startsWith("读取失败") || text.startsWith("PDF 解析失败")
                || text.startsWith("Word 解析失败");
    }

    private String parseLocal(String fileName, byte[] content) {
        String ext = extension(fileName);
        return switch (ext) {
            case ".txt", ".md", ".markdown" -> readText(content);
            case ".pdf" -> parsePdf(content);
            case ".docx" -> parseDocx(content);
            default -> throw new IllegalArgumentException(
                    "不支持的文件类型：" + ext + "（仅支持 TXT / MD / PDF / DOCX）");
        };
    }

    /** 本地 txt 读取：UTF-8 优先，非法回退 GBK（与 doc-parser readText 一致，防中文乱码） */
    private String readText(byte[] content) {
        String cs = isValidUtf8(content) ? StandardCharsets.UTF_8.name() : "GBK";
        return new String(content, Charset.forName(cs));
    }

    private boolean isValidUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (Exception e) {
            return false;
        }
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
