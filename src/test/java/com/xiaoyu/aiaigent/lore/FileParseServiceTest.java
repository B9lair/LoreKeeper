package com.xiaoyu.aiaigent.lore;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileParseService 纯本地解析测试（TXT/MD/PDF/DOCX）。
 */
class FileParseServiceTest {

    private final FileParseService service = new FileParseService();

    @Test
    void parse_txt_utf8() {
        String result = service.parse("a.txt", "正文内容".getBytes(StandardCharsets.UTF_8));
        assertEquals("正文内容", result);
    }

    @Test
    void parse_md() {
        String result = service.parse("a.md", "# 标题\n正文".getBytes(StandardCharsets.UTF_8));
        assertEquals("# 标题\n正文", result);
    }

    @Test
    void parse_unsupportedExt_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.parse("a.xyz", "x".getBytes(StandardCharsets.UTF_8)));
    }
}
