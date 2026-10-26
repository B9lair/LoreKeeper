package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.ChapterIngestResult;
import com.xiaoyu.aiaigent.lore.ChapterIngestService;
import com.xiaoyu.aiaigent.lore.ChapterService;
import com.xiaoyu.aiaigent.lore.ChapterUploadRequest;
import com.xiaoyu.aiaigent.repository.ChapterRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/upload")
    public ChapterIngestResult upload(@RequestBody ChapterUploadRequest req) {
        long workId = TenantContext.get().workId();
        return chapterIngestService.ingestChapter(workId, req.title(), req.chapterText());
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
}
