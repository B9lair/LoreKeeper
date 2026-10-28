package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 设定归并服务
 */
@Service
public class SettingMergeService {

    public enum MergeResult { DUPLICATE, NEW, UPDATE, CONFLICT }

    private static final Logger log = LoggerFactory.getLogger(SettingMergeService.class);

    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public SettingMergeService(CharacterRepository characterRepository,
                               WorldSettingRepository worldSettingRepository,
                               ChunkingService chunkingService,
                               VectorStore vectorStore,
                               JdbcTemplate jdbcTemplate) {
        this.characterRepository = characterRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 归并一条新设定断言的判定结果（纯判定，无任何写库副作用）。
     * canonicalEntity 是别名归一后的标准实体名，attribute 是 trim 后的属性名。
     */
    public record JudgeResult(MergeResult result, String canonicalEntity, String attribute, WorldSetting existing) {}

    /**
     * 纯判定：一条新设定断言相对现有设定的归并结果，不做任何落库/向量写入。
     * 逻辑与 {@link #merge} 的判定分支一致，仅剥离落库副作用，供 dry-run 一致性审查使用。
     */
    public JudgeResult judge(NewSetting s, long workId) {
        String entity = normalize(s.entity());
        String attribute = normalize(s.attribute());
        // 无实体名/属性名的断言无意义，直接忽略
        if (entity.isEmpty() || attribute.isEmpty()) {
            return new JudgeResult(MergeResult.DUPLICATE, entity, attribute, null);
        }

        Optional<CharacterRepository.Character> characterOpt =
                characterRepository.findByNameOrAlias(workId, entity);
        // 别名归一：命中别名时用标准名（character.name）比对，避免「林玄」与「主角」被拆成两个实体
        String canonicalEntity = characterOpt.map(CharacterRepository.Character::name).orElse(entity);
        boolean entityKnown = characterOpt.isPresent()
                || worldSettingRepository.existsByEntity(workId, canonicalEntity);
        Optional<WorldSetting> existing =
                worldSettingRepository.findByEntityAndAttribute(workId, canonicalEntity, attribute);

        if (!entityKnown) {
            return new JudgeResult(MergeResult.NEW, canonicalEntity, attribute, null);
        }
        if (existing.isEmpty()) {
            return new JudgeResult(MergeResult.UPDATE, canonicalEntity, attribute, null);
        }
        if (Objects.equals(existing.get().value(), s.value())) {
            return new JudgeResult(MergeResult.DUPLICATE, canonicalEntity, attribute, existing.get());
        }
        return new JudgeResult(MergeResult.CONFLICT, canonicalEntity, attribute, existing.get());
    }

    /**
     * 归并一条新设定断言：
     * - 全新实体 → NEW 入库
     * - 实体已有、attribute 新 → UPDATE 追加，version+1
     * - entity+attribute 已有、value 同 → DUPLICATE 忽略
     * - entity+attribute 已有、value 异 → CONFLICT 直接覆盖旧值（version+1 + 重建向量索引）
     */
    public MergeResult merge(NewSetting s, long workId, Integer chapterNo) {
        String entity = normalize(s.entity());
        String attribute = normalize(s.attribute());

        // 无实体名/属性名的断言无意义，直接忽略，避免污染数据
        if (entity.isEmpty() || attribute.isEmpty()) {
            return MergeResult.DUPLICATE;
        }

        // 1. 实体是否存在（character.name/aliases 或 world_setting.entity）
        Optional<CharacterRepository.Character> characterOpt =
                characterRepository.findByNameOrAlias(workId, entity);
        // 别名归一：命中别名时用标准名（character.name）落库/比对，避免「林玄」与「主角」被拆成两个实体
        String canonicalEntity = characterOpt.map(CharacterRepository.Character::name).orElse(entity);
        boolean entityKnown = characterOpt.isPresent()
                || worldSettingRepository.existsByEntity(workId, canonicalEntity);
        String type = characterOpt.isPresent() ? "person" : "rule";

        // 2. 该 canonicalEntity + attribute 是否已有断言
        Optional<WorldSetting> existing =
                worldSettingRepository.findByEntityAndAttribute(workId, canonicalEntity, attribute);

        if (!entityKnown) {
            WorldSetting saved = worldSettingRepository.save(WorldSetting.of(
                    workId, type, toContent(canonicalEntity, attribute, s.value()),
                    canonicalEntity, attribute, s.value(), s.evidence(), chapterNo, 1));
            indexSetting(workId, saved.id(), chapterNo, saved.content());
            return MergeResult.NEW;
        }

        if (existing.isEmpty()) {
            int version = worldSettingRepository.nextVersion(workId, canonicalEntity);
            WorldSetting saved = worldSettingRepository.save(WorldSetting.of(
                    workId, type, toContent(canonicalEntity, attribute, s.value()),
                    canonicalEntity, attribute, s.value(), s.evidence(), chapterNo, version));
            indexSetting(workId, saved.id(), chapterNo, saved.content());
            return MergeResult.UPDATE;
        }

        if (Objects.equals(existing.get().value(), s.value())) {
            return MergeResult.DUPLICATE;
        }

        // CONFLICT：值异 → 直接覆盖旧值（version+1 + 重建向量索引）。待确认队列已下线，改稿靠「覆盖章节」。
        WorldSetting old = existing.get();
        int version = (old.version() == null ? 0 : old.version()) + 1;
        String content = toContent(canonicalEntity, attribute, s.value());
        WorldSetting updated = new WorldSetting(old.id(), workId, old.type(), content,
                canonicalEntity, attribute, s.value(), s.evidence(), chapterNo, version);
        worldSettingRepository.update(updated);
        reindexSetting(workId, old.id(), chapterNo, content);
        return MergeResult.CONFLICT;
    }

    /**
     * 归并一条「对已有设定的更新」断言：语义等价于把 newValue 当候选值走归并。
     * - 值变 → CONFLICT（直接覆盖旧值）
     * - 值同 → DUPLICATE（忽略）
     * - 实体/属性未知 → NEW/UPDATE（兜底落库，高召回）
     */
    public MergeResult mergeUpdate(SettingUpdate u, long workId, Integer chapterNo) {
        if (u == null) {
            return MergeResult.DUPLICATE;
        }
        return merge(new NewSetting(u.entity(), u.attribute(), u.newValue(), u.evidence()), workId, chapterNo);
    }

    private String toContent(String entity, String attribute, String value) {
        return entity + " 的 " + attribute + "：" + value;
    }

    /** 设定落库后同步写向量索引（type=setting，entityId 回表）。事实源已落库，索引失败仅告警。 */
    private void indexSetting(long workId, long settingId, Integer chapterNo, String content) {
        int no = chapterNo == null ? 0 : chapterNo;
        try {
            vectorStore.add(List.of(
                    chunkingService.settingToDocument(workId, settingId, no, "setting", content)));
        } catch (Exception e) {
            log.warn("设定向量索引失败（事实源已落库，可后续重建）workId={} settingId={}: {}",
                    workId, settingId, e.getMessage());
        }
    }

    /** 覆盖式更新后重建索引：先删旧索引（entityId 相同），再写新索引 */
    private void reindexSetting(long workId, long settingId, Integer chapterNo, String content) {
        removeSettingIndex(settingId);
        indexSetting(workId, settingId, chapterNo, content);
    }

    /** 删设定向量索引：entityId 存的是字符串 */
    private void removeSettingIndex(long settingId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata ->> 'type' = 'setting' AND metadata ->> 'entityId' = ?",
                String.valueOf(settingId));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}
