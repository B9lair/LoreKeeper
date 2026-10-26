package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 伏笔表 CRUD（foreshadow）。
 */
@Repository
public class ForeshadowRepository {

    /**
     * 伏笔实体，对应 foreshadow 表。status：open → closed。
     */
    public record Foreshadow(Long id, Long workId, String content,
                             Integer plantedChapter, Integer resolvedChapter, String status) {
    }

    private final JdbcTemplate jdbcTemplate;

    public ForeshadowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Foreshadow> ROW_MAPPER = (rs, rowNum) -> new Foreshadow(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getString("content"),
            rs.getObject("planted_chapter", Integer.class),
            rs.getObject("resolved_chapter", Integer.class),
            rs.getString("status")
    );

    /** 查未回收伏笔内容（一致性审查报告的提醒项） */
    public List<String> findOpenContentsByWorkId(long workId) {
        return jdbcTemplate.queryForList(
                "SELECT content FROM foreshadow WHERE work_id = ? AND status = 'open' ORDER BY id",
                String.class, workId);
    }

    /** 列出某作品的全部伏笔（伏笔面板） */
    public List<Foreshadow> findByWorkId(long workId) {
        return jdbcTemplate.query(
                "SELECT * FROM foreshadow WHERE work_id = ? ORDER BY id",
                ROW_MAPPER, workId);
    }

    public Optional<Foreshadow> findById(long id) {
        List<Foreshadow> list = jdbcTemplate.query(
                "SELECT * FROM foreshadow WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 删除伏笔（按 id + work_id 定位，防跨作品误删），返回受影响行数 */
    public int deleteById(long id, long workId) {
        return jdbcTemplate.update("DELETE FROM foreshadow WHERE id = ? AND work_id = ?", id, workId);
    }

    /** 新增伏笔，返回自增 id（status 走 DB 默认 'open'） */
    public long save(long workId, String content, Integer plantedChapter) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO foreshadow (work_id, content, planted_chapter) VALUES (?, ?, ?) RETURNING id",
                Long.class, workId, content, plantedChapter);
    }

    /** 手动回收伏笔：status=closed + 记录回收章节，返回受影响行数 */
    public int markResolved(long id, long workId, Integer resolvedChapter) {
        return jdbcTemplate.update(
                "UPDATE foreshadow SET status = 'closed', resolved_chapter = ? WHERE id = ? AND work_id = ?",
                resolvedChapter, id, workId);
    }

    /** 该章埋设的伏笔 id 列表（删章节级联：先取 id 删向量） */
    public List<Long> findIdsByPlantedChapter(long workId, int seq) {
        return jdbcTemplate.query(
                "SELECT id FROM foreshadow WHERE work_id = ? AND planted_chapter = ?",
                (rs, rowNum) -> rs.getLong("id"), workId, seq);
    }

    /** 删除该章埋设的伏笔，返回受影响行数 */
    public int deleteByPlantedChapter(long workId, int seq) {
        return jdbcTemplate.update(
                "DELETE FROM foreshadow WHERE work_id = ? AND planted_chapter = ?", workId, seq);
    }

    /** 边界 A：在该章被回收的伏笔回退为 open，返回受影响行数 */
    public int rollbackResolved(long workId, int seq) {
        return jdbcTemplate.update(
                "UPDATE foreshadow SET status = 'open', resolved_chapter = NULL WHERE work_id = ? AND resolved_chapter = ?",
                workId, seq);
    }

    /** 删除作品下全部伏笔，返回受影响行数 */
    public int deleteByWorkId(long workId) {
        return jdbcTemplate.update("DELETE FROM foreshadow WHERE work_id = ?", workId);
    }
}
