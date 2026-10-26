package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ChapterRepository;
import com.xiaoyu.aiaigent.repository.ChapterRepository.Chapter;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.TimelineEventRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节管理（增删）。
 *
 * <p>删除语义（已确认）：删一章 = 该章「长出来的所有东西」连根拔掉——
 * 章节原文、章节向量子块、该章确立的设定（含设定向量）、该章埋设的伏笔（含伏笔向量）、
 * 该章时间线、该章产生的待确认冲突、该章审查报告 全部删除；
 * 在该章被「回收」的伏笔（resolved_chapter = 该章）回退为 open（边界 A）。</p>
 *
 * <p>事务性：pgvector（vector_store 表）与业务表同在云 RDS PostgreSQL，
 * 用同一个 JdbcTemplate 直删，整个级联放进一个 @Transactional 方法，原子完成。
 * 删除顺序：先删向量索引，再删事实源表（索引先删，失败即中止、不留孤儿）。</p>
 */
@Service
public class ChapterService {

    private static final Logger log = LoggerFactory.getLogger(ChapterService.class);

    private final ChapterRepository chapterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final ForeshadowRepository foreshadowRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final PendingConflictRepository pendingConflictRepository;
    private final JdbcTemplate jdbcTemplate;

    public ChapterService(ChapterRepository chapterRepository,
                          WorldSettingRepository worldSettingRepository,
                          ForeshadowRepository foreshadowRepository,
                          TimelineEventRepository timelineEventRepository,
                          PendingConflictRepository pendingConflictRepository,
                          JdbcTemplate jdbcTemplate) {
        this.chapterRepository = chapterRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.foreshadowRepository = foreshadowRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.pendingConflictRepository = pendingConflictRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void deleteChapter(long workId, long chapterId) {
        Chapter ch = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "章节不存在"));
        if (ch.workId() == null || ch.workId() != workId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "章节不存在或不属于当前作品");
        }
        int seq = ch.seq();

        // ① 先取该章派生的 id（删向量要用）
        List<Long> settingIds = worldSettingRepository.findIdsByChapter(workId, seq);
        List<Long> foreshadowIds = foreshadowRepository.findIdsByPlantedChapter(workId, seq);

        // ② 删向量索引（章节子块 + 设定 + 伏笔）
        deleteChapterVectors(chapterId);
        deleteEntityVectors("setting", settingIds);
        deleteEntityVectors("foreshadow", foreshadowIds);

        // ③ 删事实源表
        worldSettingRepository.deleteByChapter(workId, seq);
        foreshadowRepository.deleteByPlantedChapter(workId, seq);
        timelineEventRepository.deleteByChapter(workId, seq);
        pendingConflictRepository.deleteByChapter(workId, seq);
        jdbcTemplate.update("DELETE FROM consistency_report WHERE chapter_id = ?", chapterId);
        chapterRepository.deleteById(chapterId, workId);

        // ④ 边界 A：在该章被回收的伏笔回退 open
        foreshadowRepository.rollbackResolved(workId, seq);

        log.info("删除章节并级联清理：workId={} chapterId={} seq={} 设定={} 伏笔={}",
                workId, chapterId, seq, settingIds.size(), foreshadowIds.size());
    }

    /** 删章节向量子块：type=chapter + parentId=chapterId */
    private void deleteChapterVectors(long chapterId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata ->> 'type' = 'chapter' AND metadata ->> 'parentId' = ?",
                String.valueOf(chapterId));
    }

    /** 删设定/伏笔向量索引：type + entityId IN (...)。entityId 存字符串，故每个 id 必须加单引号，否则 PG 报 text = integer。 */
    private void deleteEntityVectors(String type, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String inClause = ids.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata ->> 'type' = ? AND metadata ->> 'entityId' IN (" + inClause + ")",
                type);
    }
}
