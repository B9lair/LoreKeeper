package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.ChapterBatchIngestResult;
import com.xiaoyu.aiaigent.lore.ChapterIngestResult;
import com.xiaoyu.aiaigent.lore.ChapterIngestService;
import com.xiaoyu.aiaigent.lore.ChapterService;
import com.xiaoyu.aiaigent.lore.ChapterUploadRequest;
import com.xiaoyu.aiaigent.lore.FileParseService;
import com.xiaoyu.aiaigent.mcp.McpClientPool;
import com.xiaoyu.aiaigent.mcp.SplitChapter;
import com.xiaoyu.aiaigent.repository.ChapterRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 章节上传 + 列表 + 删除。workId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/lore/chapter")
public class ChapterController {

    @Resource
    private ChapterIngestService chapterIngestService;

    @Resource
    private ChapterService chapterService;

    @Resource
    private ChapterRepository chapterRepository;

    @Resource
    private FileParseService fileParseService;

    @Resource
    private McpClientPool mcpClientPool;

    @PostMapping("/upload")
    public ChapterIngestResult upload(@RequestBody ChapterUploadRequest req) {
        long workId = TenantContext.get().workId();
        return chapterIngestService.ingestChapter(workId, req.title(), req.chapterText());
    }

    /**
     * 上传章节文件（TXT/MD/PDF/Word，multipart）：本地解析为纯文本后走 ingest 链路。
     * P0：整本自动切章——先本地解析全文，再调 doc-parser split_chapters 切分，逐章入库一条。
     * 8124 不在线/无章节标题时降级为「整本一章」，保持原有单章行为。前端批量 = 多文件逐个调本接口。
     */
    @PostMapping("/upload-file")
    public ChapterBatchIngestResult uploadFile(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "title", required = false) String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String text;
        try {
            text = fileParseService.parse(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败：" + e.getMessage(), e);
        }
        TenantContext.Tenant tenant = TenantContext.get();
        long workId = tenant.workId();
        String fallbackTitle = (title == null || title.isBlank())
                ? stripExtension(file.getOriginalFilename())
                : title.trim();

        List<SplitChapter> chapters = normalizeParts(
                mcpClientPool.splitChapters(tenant.userId(), workId, text), fallbackTitle);
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("章节正文不能为空");
        }

        List<ChapterIngestResult> results = new ArrayList<>();
        for (SplitChapter c : chapters) {
            results.add(chapterIngestService.ingestChapter(workId, c.title(), c.content()));
        }
        return aggregate(results);
    }

    /** 列出当前作品的章节（按 seq 升序），供作品管理/章节上传页展示 */
    @GetMapping("/list")
    public List<ChapterRepository.Chapter> list() {
        return chapterRepository.findByWorkId(TenantContext.get().workId());
    }

    /** 删除章节：级联删除该章原文、向量、设定、伏笔、时间线、审查报告 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        chapterService.deleteChapter(TenantContext.get().workId(), id);
    }

    /** 覆盖式替换章节：级联删除旧章（设定/伏笔库也会被完全覆盖）后用原 seq 重新入库 */
    @PostMapping("/{id}/replace")
    public ChapterIngestResult replace(@PathVariable long id, @RequestBody ChapterUploadRequest req) {
        return chapterService.replaceChapter(TenantContext.get().workId(), id, req.title(), req.chapterText());
    }

    /** 归一化切分结果：过滤空章；占位标题（null/空/「全文」）回填为 fallbackTitle（单章时保留文件名语义） */
    private List<SplitChapter> normalizeParts(List<SplitChapter> parts, String fallbackTitle) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<SplitChapter> out = new ArrayList<>();
        for (SplitChapter p : parts) {
            String content = p.content() == null ? "" : p.content().trim();
            if (content.isEmpty()) {
                continue;
            }
            String t = p.title();
            boolean placeholder = t == null || t.isBlank() || "全文".equals(t.trim());
            out.add(new SplitChapter(placeholder ? fallbackTitle : t.trim(), content));
        }
        return out;
    }

    /** 聚合多章入库结果 */
    private ChapterBatchIngestResult aggregate(List<ChapterIngestResult> results) {
        int ns = 0, us = 0, cf = 0, fs = 0;
        for (ChapterIngestResult r : results) {
            ns += r.newSettings();
            us += r.updatedSettings();
            cf += r.conflicts();
            fs += r.foreshadows();
        }
        return new ChapterBatchIngestResult(results.size(), results, ns, us, cf, fs);
    }

    private String stripExtension(String name) {
        if (name == null) return null;
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
