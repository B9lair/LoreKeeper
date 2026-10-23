package com.xiaoyu.aiaigent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO character (work_id, name, aliases, role, ability, first_appear_chapter, status) " +
                            "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, character.workId());
            ps.setString(2, character.name());
            ps.setString(3, toJson(character.aliases()));
            ps.setString(4, character.role());
            ps.setString(5, character.ability());
            ps.setObject(6, character.firstAppearChapter());
            ps.setString(7, character.status());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入 character 后未返回自增主键");
        }
        return findById(key.longValue()).orElseThrow();
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

    /** 更新（按 id + work_id 定位，防跨作品误改），返回受影响行数 */
    public int update(Character character) {
        return jdbcTemplate.update(
                "UPDATE character SET name = ?, aliases = ?::jsonb, role = ?, ability = ?, " +
                        "first_appear_chapter = ?, status = ? WHERE id = ? AND work_id = ?",
                character.name(), toJson(character.aliases()), character.role(), character.ability(),
                character.firstAppearChapter(), character.status(), character.id(), character.workId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM character WHERE id = ?", id);
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
