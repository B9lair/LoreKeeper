package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设定条目表 CRUD
 */
@Repository
public class WorldSettingRepository {

    public record WorldSetting(
            Long id,
            Long workId,
            String type,
            String content,
            String entity,
            String attribute,
            String value,
            String evidence,
            Integer establishedChapter,
            Integer version
    ) {
        public static WorldSetting of(Long workId, String type, String content,
                                      String entity, String attribute, String value,
                                      String evidence, Integer establishedChapter, Integer version) {
            return new WorldSetting(null, workId, type, content, entity, attribute, value,
                    evidence, establishedChapter, version);
        }
    }

    private final JdbcTemplate jdbcTemplate;

    public WorldSettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<WorldSetting> ROW_MAPPER = (rs, rowNum) -> new WorldSetting(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getString("type"),
            rs.getString("content"),
            rs.getString("entity"),
            rs.getString("attribute"),
            rs.getString("value"),
            rs.getString("evidence"),
            rs.getObject("established_chapter", Integer.class),
            rs.getObject("version", Integer.class)
    );

    public WorldSetting save(WorldSetting ws) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO world_setting (work_id, type, content, entity, attribute, value, evidence, established_chapter, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                ws.workId(), ws.type(), ws.content(), ws.entity(), ws.attribute(),
                ws.value(), ws.evidence(), ws.establishedChapter(), ws.version());
        return findById(id).orElseThrow();
    }

    public Optional<WorldSetting> findById(Long id) {
        List<WorldSetting> list = jdbcTemplate.query(
                "SELECT * FROM world_setting WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public List<WorldSetting> findByEntity(Long workId, String entity) {
        return jdbcTemplate.query(
                "SELECT * FROM world_setting WHERE work_id = ? AND entity = ? ORDER BY id",
                ROW_MAPPER, workId, entity);
    }

    public Optional<WorldSetting> findByEntityAndAttribute(Long workId, String entity, String attribute) {
        List<WorldSetting> list = jdbcTemplate.query(
                "SELECT * FROM world_setting WHERE work_id = ? AND entity = ? AND attribute = ? ORDER BY id DESC",
                ROW_MAPPER, workId, entity, attribute);
        return list.stream().findFirst();
    }

    public boolean existsByEntity(Long workId, String entity) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM world_setting WHERE work_id = ? AND entity = ?",
                Long.class, workId, entity);
        return count != null && count > 0;
    }

    /** 该实体下一个版本号 */
    public int nextVersion(Long workId, String entity) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM world_setting WHERE work_id = ? AND entity = ?",
                Integer.class, workId, entity);
        return (max == null ? 0 : max) + 1;
    }

    /** 列出某作品的全部设定条目（设定管理页） */
    public List<WorldSetting> findByWorkId(Long workId) {
        return jdbcTemplate.query(
                "SELECT * FROM world_setting WHERE work_id = ? ORDER BY id",
                ROW_MAPPER, workId);
    }

    /** 更新设定（按 id + work_id 定位，防跨作品误改），返回受影响行数 */
    public int update(WorldSetting ws) {
        return jdbcTemplate.update(
                "UPDATE world_setting SET type = ?, content = ?, entity = ?, attribute = ?, value = ?, " +
                        "evidence = ?, established_chapter = ?, version = ? WHERE id = ? AND work_id = ?",
                ws.type(), ws.content(), ws.entity(), ws.attribute(), ws.value(),
                ws.evidence(), ws.establishedChapter(), ws.version(), ws.id(), ws.workId());
    }

    /** 删除设定（按 id + work_id 定位，防跨作品误删），返回受影响行数 */
    public int deleteById(Long id, Long workId) {
        return jdbcTemplate.update("DELETE FROM world_setting WHERE id = ? AND work_id = ?", id, workId);
    }

    /** 该章确立的设定 id 列表（删章节级联：先取 id 删向量） */
    public List<Long> findIdsByChapter(long workId, int seq) {
        return jdbcTemplate.query(
                "SELECT id FROM world_setting WHERE work_id = ? AND established_chapter = ?",
                (rs, rowNum) -> rs.getLong("id"), workId, seq);
    }

    /** 删除该章确立的设定，返回受影响行数 */
    public int deleteByChapter(long workId, int seq) {
        return jdbcTemplate.update(
                "DELETE FROM world_setting WHERE work_id = ? AND established_chapter = ?", workId, seq);
    }

    /** 删除作品下全部设定，返回受影响行数 */
    public int deleteByWorkId(long workId) {
        return jdbcTemplate.update("DELETE FROM world_setting WHERE work_id = ?", workId);
    }
}
