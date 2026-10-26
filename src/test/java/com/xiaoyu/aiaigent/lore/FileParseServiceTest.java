package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileParseService P1 降级逻辑测试：优先走 doc-parser parse_document_content，
 * 离线/失败/无租户时降级本地解析。
 */
class FileParseServiceTest {

    private final McpClientPool mcpClientPool = mock(McpClientPool.class);
    private final FileParseService service = new FileParseService(mcpClientPool);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void parse_mcpPreferred() {
        TenantContext.set(new TenantContext.Tenant(1L, 100L));
        when(mcpClientPool.parseDocumentContent(eq(1L), eq(100L), eq("a.txt"), any(byte[].class)))
                .thenReturn("来自 MCP 的正文");

        String result = service.parse("a.txt", "原文".getBytes(StandardCharsets.UTF_8));

        assertEquals("来自 MCP 的正文", result);
        verify(mcpClientPool).parseDocumentContent(eq(1L), eq(100L), eq("a.txt"), any(byte[].class));
    }

    @Test
    void parse_mcpNull_fallbackLocal() {
        TenantContext.set(new TenantContext.Tenant(1L, 100L));
        when(mcpClientPool.parseDocumentContent(anyLong(), anyLong(), anyString(), any(byte[].class)))
                .thenReturn(null);

        String result = service.parse("a.txt", "本地正文".getBytes(StandardCharsets.UTF_8));

        assertEquals("本地正文", result);
    }

    @Test
    void parse_mcpError_fallbackLocal() {
        TenantContext.set(new TenantContext.Tenant(1L, 100L));
        // doc-parser 返回错误文案（如类型不支持），应降级本地解析
        when(mcpClientPool.parseDocumentContent(anyLong(), anyLong(), anyString(), any(byte[].class)))
                .thenReturn("不支持的类型：.txt（仅支持 TXT/MD/PDF/DOCX）");

        String result = service.parse("a.txt", "本地兜底".getBytes(StandardCharsets.UTF_8));

        assertEquals("本地兜底", result);
    }

    @Test
    void parse_noTenant_fallbackLocal() {
        // 不初始化 TenantContext：parse 内部 get() 抛异常 → 降级本地解析
        String result = service.parse("a.txt", "无租户本地正文".getBytes(StandardCharsets.UTF_8));

        assertEquals("无租户本地正文", result);
    }

    @Test
    void parse_unsupportedExt_throws() {
        TenantContext.set(new TenantContext.Tenant(1L, 100L));
        when(mcpClientPool.parseDocumentContent(anyLong(), anyLong(), anyString(), any(byte[].class)))
                .thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.parse("a.xyz", "x".getBytes(StandardCharsets.UTF_8)));
    }
}
