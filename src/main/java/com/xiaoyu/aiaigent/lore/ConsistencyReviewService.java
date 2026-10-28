package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 一致性审查
 * ① 抽取 → ② evidence 过滤（在 extract 内完成）→ ③ 归并 → ④ CONFLICT 比对 → ⑤ 查未回收伏笔
 */
@Service
public class ConsistencyReviewService {

    private final EntityExtractor extractor;
    private final SettingMergeService mergeService;
    private final ForeshadowRepository foreshadowRepository;

    public ConsistencyReviewService(EntityExtractor extractor,
                                    SettingMergeService mergeService,
                                    ForeshadowRepository foreshadowRepository) {
        this.extractor = extractor;
        this.mergeService = mergeService;
        this.foreshadowRepository = foreshadowRepository;
    }

    /**
     * dry-run 一致性审查（只读，不落库）：供对话 Agent 的一致性检测工具调用。
     * 纯判定（judge），不落库、不写向量、不落待确认队列。
     */
    public ConsistencyReport reviewChapterDryRun(long workId, Integer chapterNo, String chapterText) {
        ChapterExtraction ex = extractor.extract(chapterText);   // ① 抽取（内含 ② evidence 过滤）

        List<Conflict> conflicts = new ArrayList<>();
        for (NewSetting s : ex.settings()) {
            collectDryRunConflict(s, workId, chapterNo, conflicts);
        }
        for (SettingUpdate u : ex.settingUpdates()) {
            collectDryRunConflict(new NewSetting(u.entity(), u.attribute(), u.newValue(), u.evidence()),
                    workId, chapterNo, conflicts);
        }

        List<String> reminders = foreshadowRepository.findOpenContentsByWorkId(workId); // ⑤ 查伏笔
        return new ConsistencyReport(conflicts, reminders);
    }

    private void collectDryRunConflict(NewSetting s, long workId, Integer chapterNo, List<Conflict> conflicts) {
        SettingMergeService.JudgeResult j = mergeService.judge(s, workId);
        if (j.result() == SettingMergeService.MergeResult.CONFLICT) {
            // 用归一化实体名/属性，避免别名（"主角"）与标准名（"林玄"）不一致
            NewSetting normalized = new NewSetting(j.canonicalEntity(), j.attribute(), s.value(), s.evidence());
            conflicts.add(compare(normalized, j.existing(), chapterNo));
        }
    }

    /**
     * 比对本章该实体断言 和 该实体历史断言
     */
    private Conflict compare(NewSetting s, WorldSetting hist, Integer chapterNo) {
        String descA = s.attribute() + "=" + s.value();
        String descB = hist == null ? "?" : hist.attribute() + "=" + hist.value();
        String chapterA = chapterNo == null ? "?" : String.valueOf(chapterNo);
        String chapterB = hist == null || hist.establishedChapter() == null
                ? "?" : String.valueOf(hist.establishedChapter());

        return new Conflict(s.entity(), descA, descB, chapterA, chapterB);
    }
}
