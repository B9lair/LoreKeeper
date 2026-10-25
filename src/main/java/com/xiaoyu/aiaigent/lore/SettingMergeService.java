package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.CharacterRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 设定归并服务
 */
@Service
public class SettingMergeService {

    public enum MergeResult { DUPLICATE, NEW, UPDATE, CONFLICT }

    private final CharacterRepository characterRepository;
    private final WorldSettingRepository worldSettingRepository;

    public SettingMergeService(CharacterRepository characterRepository,
                               WorldSettingRepository worldSettingRepository) {
        this.characterRepository = characterRepository;
        this.worldSettingRepository = worldSettingRepository;
    }

    /**
     * 归并一条新设定断言：
     * - 全新实体 → NEW 入库
     * - 实体已有、attribute 新 → UPDATE 追加，version+1
     * - entity+attribute 已有、value 同 → DUPLICATE 忽略
     * - entity+attribute 已有、value 异 → CONFLICT 不落库，交一致性审查
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
        boolean entityKnown = characterOpt.isPresent()
                || worldSettingRepository.existsByEntity(workId, entity);
        String type = characterOpt.isPresent() ? "person" : "rule";

        // 2. 该 entity + attribute 是否已有断言
        Optional<WorldSetting> existing =
                worldSettingRepository.findByEntityAndAttribute(workId, entity, attribute);

        if (!entityKnown) {
            worldSettingRepository.save(WorldSetting.of(
                    workId, type, toContent(entity, attribute, s.value()),
                    entity, attribute, s.value(), s.evidence(), chapterNo, 1));
            return MergeResult.NEW;
        }

        if (existing.isEmpty()) {
            int version = worldSettingRepository.nextVersion(workId, entity);
            worldSettingRepository.save(WorldSetting.of(
                    workId, type, toContent(entity, attribute, s.value()),
                    entity, attribute, s.value(), s.evidence(), chapterNo, version));
            return MergeResult.UPDATE;
        }

        if (Objects.equals(existing.get().value(), s.value())) {
            return MergeResult.DUPLICATE;
        }
        return MergeResult.CONFLICT;
    }

    private String toContent(String entity, String attribute, String value) {
        return entity + " 的 " + attribute + "：" + value;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}
