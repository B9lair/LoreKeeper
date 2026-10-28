package com.xiaoyu.aiaigent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色表 CRUD。
 */
@Repository
public class CharacterRepository {

    /**
     * 角色实体，对应 character 表。aliases 为 JSONB（字符串数组）。
     */
    public record Character(
            Long id,
            Long workId,
            String name,
            List<String> aliases,
            String role,
            String ability,
            Integer firstAppearChapter,
            String status
    ) {
        /** 新建用（id 由数据库自增生成） */
        public static Character of(Long workId, String name, List<String> aliases,
                                   String role, String ability, Integer firstAppearChapter, String status) {
            return new Character(null, workId, name, aliases, role, ability, firstAppearChapter, status);
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CharacterRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<Character> ROW_MAPPER = (rs, rowNum) -> new Character(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getString("name"),
            parseAliases(rs.getString("aliases")),
            rs.getString("role"),
            rs.getString("ability"),
            rs.getObject("first_appear_chapter", Integer.class),
            rs.getString("status")
    );

    /** 新增，返回带自增 id 的实体 */
    public Character save(Character character) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id",
                Long.class,
                character.workId(), character.name(), toJson(character.aliases()),
                character.role(), character.ability(), character.firstAppearChapter(), character.status());
        return findById(id).orElseThrow();
    }

    public Optional<Character> findById(Long id) {
        List<Character> list = jdbcTemplate.query(
                "SELECT * FROM character WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<Character> findByWorkId(Long workId) {
        return jdbcTemplate.query(
                "SELECT * FROM character WHERE work_id = ? ORDER BY id", ROW_MAPPER, workId);
    }

    /** 按作品 + 姓名精确查找 */
    public Optional<Character> findByName(Long workId, String name) {
        List<Character> list = jdbcTemplate.query(
                "SELECT * FROM character WHERE work_id = ? AND name = ?", ROW_MAPPER, workId, name);
        return list.stream().findFirst();
    }

        /** 按作品 + 姓名/别名查找（归并用：实体名 + aliases） */
    public Optional<Character> findByNameOrAlias(Long workId, String name) {
        List<Character> list = jdbcTemplate.query(
                "SELECT * FROM character WHERE work_id = ? AND (name = ? OR EXISTS " +
                "(SELECT 1 FROM jsonb_array_elements_text(aliases) a WHERE a = ?))",
                ROW_MAPPER, workId, name, name);
        return list.stream().findFirst();
    }

    /**
     * 列出「回填候选」：world_setting 里出现过的实体名，且尚未成为角色（不在 character 的 name/aliases 中），
     * 且未被人工忽略（不在 character_ignored 中）。
     * 供「角色导入」人工确认用——候选里可能混有地点/门派等非人物，交由人工勾选裁决或忽略。
     */
    public List<String> findCandidateNames(Long workId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT ws.entity FROM world_setting ws " +
                "WHERE ws.work_id = ? AND ws.entity IS NOT NULL " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM character c WHERE c.work_id = ws.work_id " +
                "  AND (c.name = ws.entity OR EXISTS (" +
                "    SELECT 1 FROM jsonb_array_elements_text(c.aliases) a WHERE a = ws.entity" +
                "  ))" +
                ") " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM character_ignored ig WHERE ig.work_id = ws.work_id AND ig.entity = ws.entity" +
                ") ORDER BY ws.entity",
                String.class, workId);
    }

    /** 忽略候选实体（确认为非人物）：幂等插入，已存在则忽略 */
    public void ignoreEntity(Long workId, String entity) {
        jdbcTemplate.update(
                "INSERT INTO character_ignored (work_id, entity) VALUES (?, ?) " +
                "ON CONFLICT (work_id, entity) DO NOTHING",
                workId, entity);
    }

    /** 恢复被忽略的候选实体 */
    public void restoreEntity(Long workId, String entity) {
        jdbcTemplate.update(
                "DELETE FROM character_ignored WHERE work_id = ? AND entity = ?", workId, entity);
    }

    /** 列出已忽略的实体名（供前端展示/恢复） */
    public List<String> findIgnoredNames(Long workId) {
        return jdbcTemplate.queryForList(
                "SELECT entity FROM character_ignored WHERE work_id = ? ORDER BY entity",
                String.class, workId);
    }


    /** 更新（按 id + work_id 定位，防跨作品误改），返回受影响行数 */
    public int update(Character character) {
        return jdbcTemplate.update(
                "UPDATE character SET name = ?, aliases = ?::jsonb, role = ?, ability = ?, " +
                        "first_appear_chapter = ?, status = ? WHERE id = ? AND work_id = ?",
                character.name(), toJson(character.aliases()), character.role(), character.ability(),
                character.firstAppearChapter(), character.status(), character.id(), character.workId());
    }

    private String toJson(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(aliases);
        } catch (Exception e) {
            throw new IllegalArgumentException("序列化 aliases 失败", e);
        }
    }

    private List<String> parseAliases(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("解析 aliases 失败: " + json, e);
        }
    }
}
