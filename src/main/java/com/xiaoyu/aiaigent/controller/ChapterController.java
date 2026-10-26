package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.ChapterIngestResult;
import com.xiaoyu.aiaigent.lore.ChapterIngestService;
import com.xiaoyu.aiaigent.lore.ChapterService;
import com.xiaoyu.aiaigent.lore.ChapterUploadRequest;
import com.xiaoyu.aiaigent.lore.FileParseService;
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

    @PostMapping("/upload")
    public ChapterIngestResult upload(@RequestBody ChapterUploadRequest req) {
        long workId = TenantContext.get().workId();
        return chapterIngestService.ingestChapter(workId, req.title(), req.chapterText());
    }

    /**
     * 上传章节文件（TXT/MD/PDF/Word，multipart）：本地解析为纯文本后走 ingest 链路。
     * 前端批量 = 多文件逐个调本接口；标题缺省时用文件名（去扩展名）。
     */
    @PostMapping("/upload-file")
    public ChapterIngestResult uploadFile(@RequestParam("file") MultipartFile file,
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
        long workId = TenantContext.get().workId();
        String actualTitle = (title == null || title.isBlank())
                ? stripExtension(file.getOriginalFilename())
                : title.trim();
        return chapterIngestService.ingestChapter(workId, actualTitle, text);
    }

    /** 列出当前作品的章节（按 seq 升序），供作品管理/章节上传页展示 */
    @GetMapping("/list")
    public List<ChapterRepository.Chapter> list() {
        return chapterRepository.findByWorkId(TenantContext.get().workId());
    }

    /** 删除章节：级联删除该章原文、向量、设定、伏笔、时间线、待确认冲突、审查报告 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        chapterService.deleteChapter(TenantContext.get().workId(), id);
    }

    private String stripExtension(String name) {
        if (name == null) return null;
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
