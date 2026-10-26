package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 时间线事件表 CRUD（timeline_event）。
 */
@Repository
public class TimelineEventRepository {

    /**
     * 时间线事件行，对应 timeline_event 表。
     * 注意：抽取结果里的 evidence 仅用于反幻觉过滤，不落库（表无此列）。
     */
    public record TimelineEventRow(Long id, Long workId, String timePoint, String event, Integer chapter) {
    }

    private final JdbcTemplate jdbcTemplate;

    public TimelineEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TimelineEventRow> ROW_MAPPER = (rs, rowNum) -> new TimelineEventRow(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getString("time_point"),
            rs.getString("event"),
            rs.getObject("chapter", Integer.class)
    );

    /** 新增时间线事件，返回自增 id（PostgreSQL RETURNING） */
    public long save(long workId, String timePoint, String event, Integer chapter) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO timeline_event (work_id, time_point, event, chapter) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, workId, timePoint, event, chapter);
    }

    public List<TimelineEventRow> findByWorkId(long workId) {
        return jdbcTemplate.query(
                "SELECT * FROM timeline_event WHERE work_id = ? ORDER BY id",
                ROW_MAPPER, workId);
    }

    /** 删除该章时间线事件，返回受影响行数 */
    public int deleteByChapter(long workId, int seq) {
        return jdbcTemplate.update(
                "DELETE FROM timeline_event WHERE work_id = ? AND chapter = ?", workId, seq);
    }

    /** 删除作品下全部时间线事件，返回受影响行数 */
    public int deleteByWorkId(long workId) {
        return jdbcTemplate.update("DELETE FROM timeline_event WHERE work_id = ?", workId);
    }
}
