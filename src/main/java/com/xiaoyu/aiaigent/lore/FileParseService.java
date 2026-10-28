package com.xiaoyu.aiaigent.lore;

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
 * 章节文件解析（TXT/MD/PDF/DOCX → 纯文本），纯本地解析（PDFBox / POI）。
 * 上传章节入库、一致性审查都是确定性动作。
 */
@Service
public class FileParseService {

    public String parse(String fileName, byte[] content) {
        return parseLocal(fileName, content);
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

    /** 本地 txt 读取：UTF-8 优先，非法回退 GBK（防中文乱码） */
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
