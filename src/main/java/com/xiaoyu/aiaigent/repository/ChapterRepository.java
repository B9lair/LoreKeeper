package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 章节表 CRUD（chapter）。
 */
@Repository
public class ChapterRepository {

    /**
     * 章节实体，对应 chapter 表。content 为整章原文（方案 B 补的列）。
     */
    public record Chapter(Long id, Long workId, Integer seq, String title,
                          String status, String content) {
    }

    private final JdbcTemplate jdbcTemplate;

    public ChapterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Chapter> ROW_MAPPER = (rs, rowNum) -> new Chapter(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getObject("seq", Integer.class),
            rs.getString("title"),
            rs.getString("status"),
            rs.getString("content")
    );

    /** 新增章节，返回自增 id（PostgreSQL RETURNING）；status 传 null 时走 DB 默认 'draft' */
    public long save(long workId, int seq, String title, String status, String content) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO chapter (work_id, seq, title, status, content) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, workId, seq, title, status, content);
    }

    /** 列出某作品的全部章节，按 seq 升序 */
    public List<Chapter> findByWorkId(long workId) {
        return jdbcTemplate.query(
                "SELECT * FROM chapter WHERE work_id = ? ORDER BY seq",
                ROW_MAPPER, workId);
    }

    public Optional<Chapter> findById(long id) {
        List<Chapter> list = jdbcTemplate.query(
                "SELECT * FROM chapter WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 该作品下一个章节序号（MAX(seq) + 1） */
    public int nextSeq(long workId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(seq), 0) FROM chapter WHERE work_id = ?",
                Integer.class, workId);
        return (max == null ? 0 : max) + 1;
    }

    /** 删除章节（按 id + work_id 定位，防跨作品误删），返回受影响行数 */
    public int deleteById(long id, long workId) {
        return jdbcTemplate.update("DELETE FROM chapter WHERE id = ? AND work_id = ?", id, workId);
    }

    /** 删除作品下全部章节，返回受影响行数 */
    public int deleteByWorkId(long workId) {
        return jdbcTemplate.update("DELETE FROM chapter WHERE work_id = ?", workId);
    }
}
