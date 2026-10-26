package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.ChapterRepository;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.TimelineEventRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 章节上传入库链路（2.6.7 尽力一致性落地）：
 * ① 存 chapter 表（含整章原文）→ ② 章节段落子块向量化（失败补偿：删 chapter）→ ③ 抽取后落库设定/伏笔/时间线（失败不阻塞）。
 */
@Service
public class ChapterIngestService {

    private static final Logger log = LoggerFactory.getLogger(ChapterIngestService.class);

    private final ChapterRepository chapterRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final EntityExtractor entityExtractor;
    private final SettingMergeService mergeService;
    private final ForeshadowRepository foreshadowRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final PendingConflictRepository pendingConflictRepository;

    public ChapterIngestService(ChapterRepository chapterRepository,
                                ChunkingService chunkingService,
                                VectorStore vectorStore,
                                EntityExtractor entityExtractor,
                                SettingMergeService mergeService,
                                ForeshadowRepository foreshadowRepository,
                                TimelineEventRepository timelineEventRepository,
                                WorldSettingRepository worldSettingRepository,
                                PendingConflictRepository pendingConflictRepository) {
        this.chapterRepository = chapterRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.entityExtractor = entityExtractor;
        this.mergeService = mergeService;
        this.foreshadowRepository = foreshadowRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.pendingConflictRepository = pendingConflictRepository;
    }

    public ChapterIngestResult ingestChapter(long workId, String title, String chapterText) {
        if (chapterText == null || chapterText.isBlank()) {
            throw new IllegalArgumentException("章节正文不能为空");
        }

        int seq = chapterRepository.nextSeq(workId);
        String actualTitle = (title == null || title.isBlank()) ? "第 " + seq + " 章" : title;
        long chapterId = chapterRepository.save(workId, seq, actualTitle, "draft", chapterText);

        // ② 章节向量化（失败补偿：删除刚写入的 chapter 记录）
        try {
            List<Document> docs = chunkingService.chunkChapter(workId, chapterId, seq, chapterText);
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
            }
        } catch (Exception e) {
            chapterRepository.deleteById(chapterId, workId);
            throw new IllegalStateException("章节向量化失败，已回滚章节记录: " + e.getMessage(), e);
        }

        // ③ 抽取 + 落库（尽力，失败不阻塞章节入库主线）
        IngestStats stats = ingestExtraction(workId, seq, chapterText);

        return new ChapterIngestResult(chapterId, seq, actualTitle,
                stats.newSettings(), stats.updatedSettings(), stats.conflicts(), stats.foreshadows());
    }

    private IngestStats ingestExtraction(long workId, int seq, String chapterText) {
        try {
            ChapterExtraction ex = entityExtractor.extract(chapterText);
            int newSettings = 0, updatedSettings = 0, conflicts = 0;

            // 新设定断言 + 对已有设定的更新断言，统一走归并
            for (NewSetting s : ex.settings()) {
                switch (handleMerge(mergeService.merge(s, workId, seq), s, workId, seq)) {
                    case NEW -> newSettings++;
                    case UPDATE -> updatedSettings++;
                    case CONFLICT -> conflicts++;
                    default -> { }
                }
            }
            for (SettingUpdate u : ex.settingUpdates()) {
                NewSetting asNew = new NewSetting(u.entity(), u.attribute(), u.newValue(), u.evidence());
                switch (handleMerge(mergeService.mergeUpdate(u, workId, seq), asNew, workId, seq)) {
                    case NEW -> newSettings++;
                    case UPDATE -> updatedSettings++;
                    case CONFLICT -> conflicts++;
                    default -> { }
                }
            }
            for (Foreshadow f : ex.foreshadows()) {
                long fid = foreshadowRepository.save(workId, f.content(), seq);
                indexForeshadow(workId, fid, seq, f.content());
            }
            for (TimelineEvent t : ex.timeline()) {
                timelineEventRepository.save(workId, t.timePoint(), t.event(), seq);
            }

            return new IngestStats(newSettings, updatedSettings, conflicts, ex.foreshadows().size());
        } catch (Exception e) {
            log.warn("章节抽取落库失败（不影响章节入库，workId={}, seq={}）: {}", workId, seq, e.getMessage());
            return new IngestStats(0, 0, 0, 0);
        }
    }

    /**
     * CONFLICT 落待确认队列（2.6.6 人机协同）；NEW/UPDATE/DUPLICATE 不额外处理。
     * 返回归并结果供调用方统计。
     */
    private SettingMergeService.MergeResult handleMerge(SettingMergeService.MergeResult result, NewSetting s,
                                                        long workId, Integer chapterNo) {
        if (result == SettingMergeService.MergeResult.CONFLICT) {
            WorldSetting hist = worldSettingRepository
                    .findByEntityAndAttribute(workId, s.entity(), s.attribute())
                    .orElse(null);
            pendingConflictRepository.save(workId, s.entity(), s.attribute(),
                    hist == null ? null : hist.value(), s.value(), s.evidence(), chapterNo);
        }
        return result;
    }

    private void indexForeshadow(long workId, long fid, int seq, String content) {
        try {
            vectorStore.add(List.of(chunkingService.settingToDocument(workId, fid, seq, "foreshadow", content)));
        } catch (Exception e) {
            log.warn("伏笔向量索引失败（事实源已在表里，可后续重建）: {}", e.getMessage());
        }
    }

    /** 抽取落库统计（供上传响应回传）。 */
    private record IngestStats(int newSettings, int updatedSettings, int conflicts, int foreshadows) {
    }
}
