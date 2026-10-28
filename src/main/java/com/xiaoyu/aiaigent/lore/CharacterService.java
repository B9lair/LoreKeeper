package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.CharacterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色档案管理：列表 / 回填候选 / 人工确认导入。
 * 回填用于把历史 world_setting 里已抽取、但从未同步到 character 表的实体，
 * 经人工勾选（区分人物与地点）后一次性补进 character 表。
 */
@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    public List<CharacterRepository.Character> list(long workId) {
        return characterRepository.findByWorkId(workId);
    }

    /** 回填候选：world_setting 里出现、但尚未成为角色的实体名（含地点等，需人工裁决） */
    public List<String> candidates(long workId) {
        return characterRepository.findCandidateNames(workId);
    }

    /** 人工确认导入：逐个 upsert（已存在则跳过，不覆盖），返回实际新增数量 */
    public int backfill(long workId, List<CharacterRef> characters) {
        if (characters == null) {
            return 0;
        }
        int added = 0;
        for (CharacterRef c : characters) {
            if (c == null || c.name() == null || c.name().isBlank()) {
                continue;
            }
            String name = c.name().trim();
            if (characterRepository.findByNameOrAlias(workId, name).isPresent()) {
                continue;
            }
            characterRepository.save(CharacterRepository.Character.of(
                    workId, name, c.aliases(), null, null, null, null));
            added++;
        }
        return added;
    }

    /** 已忽略的候选实体名列表 */
    public List<String> ignored(long workId) {
        return characterRepository.findIgnoredNames(workId);
    }

    /** 忽略候选实体（确认为非人物，从候选列表隐藏） */
    public void ignore(long workId, String entity) {
        if (entity == null || entity.isBlank()) {
            return;
        }
        characterRepository.ignoreEntity(workId, entity.trim());
    }

    /** 恢复被忽略的候选实体 */
    public void restore(long workId, String entity) {
        if (entity == null || entity.isBlank()) {
            return;
        }
        characterRepository.restoreEntity(workId, entity.trim());
    }
}
