package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
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
    private final WorldSettingRepository worldSettingRepository;
    private final ForeshadowRepository foreshadowRepository;

    public ConsistencyReviewService(EntityExtractor extractor,
                                    SettingMergeService mergeService,
                                    WorldSettingRepository worldSettingRepository,
                                    ForeshadowRepository foreshadowRepository) {
        this.extractor = extractor;
        this.mergeService = mergeService;
        this.worldSettingRepository = worldSettingRepository;
        this.foreshadowRepository = foreshadowRepository;
    }

    public ConsistencyReport reviewChapter(long workId, Integer chapterNo, String chapterText) {
        ChapterExtraction ex = extractor.extract(chapterText);   // ① 抽取（内含 ② evidence 过滤）

        List<Conflict> conflicts = new ArrayList<>();
        for (NewSetting s : ex.settings()) {
            if (mergeService.merge(s, workId, chapterNo) == SettingMergeService.MergeResult.CONFLICT) {
                conflicts.add(compare(s, workId, chapterNo));     // ③ 归并 + ④ 逐条比对
            }
        }

        List<String> reminders = foreshadowRepository.findOpenContentsByWorkId(workId); // ⑤ 查伏笔
        return new ConsistencyReport(conflicts, reminders);
    }

    /**
     * 比对本章该实体断言 和 该实体历史断言
     */
    private Conflict compare(NewSetting s, long workId, Integer chapterNo) {
        WorldSetting hist = worldSettingRepository
                .findByEntityAndAttribute(workId, s.entity(), s.attribute())
                .orElse(null);

        String descA = s.attribute() + "=" + s.value();
        String descB = hist == null ? "?" : hist.attribute() + "=" + hist.value();
        String chapterA = chapterNo == null ? "?" : String.valueOf(chapterNo);
        String chapterB = hist == null || hist.establishedChapter() == null
                ? "?" : String.valueOf(hist.establishedChapter());

        return new Conflict(s.entity(), descA, descB, chapterA, chapterB);
    }
}
