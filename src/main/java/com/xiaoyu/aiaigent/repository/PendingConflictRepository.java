package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 待确认冲突表 CRUD（pending_conflict）。
 * status：pending → accepted / rejected。
 */
@Repository
public class PendingConflictRepository {

    public record PendingConflict(Long id, Long workId, String entity, String attribute,
                                  String oldValue, String newValue, String evidence,
                                  Integer chapterNo, String status) {
    }

    private final JdbcTemplate jdbcTemplate;

    public PendingConflictRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PendingConflict> ROW_MAPPER = (rs, rowNum) -> new PendingConflict(
            rs.getObject("id", Long.class),
            rs.getObject("work_id", Long.class),
            rs.getString("entity"),
            rs.getString("attribute"),
            rs.getString("old_value"),
            rs.getString("new_value"),
            rs.getString("evidence"),
            rs.getObject("chapter_no", Integer.class),
            rs.getString("status")
    );

    /** 新增待确认冲突，返回自增 id（status 走 DB 默认 'pending'） */
    public long save(long workId, String entity, String attribute,
                     String oldValue, String newValue, String evidence, Integer chapterNo) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO pending_conflict (work_id, entity, attribute, old_value, new_value, evidence, chapter_no) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, workId, entity, attribute, oldValue, newValue, evidence, chapterNo);
    }

    /** 队列列表（按 status 过滤） */
    public List<PendingConflict> findByWorkId(long workId, String status) {
        return jdbcTemplate.query(
                "SELECT * FROM pending_conflict WHERE work_id = ? AND status = ? ORDER BY id",
                ROW_MAPPER, workId, status);
    }

    public Optional<PendingConflict> findById(long id, long workId) {
        List<PendingConflict> list = jdbcTemplate.query(
                "SELECT * FROM pending_conflict WHERE id = ? AND work_id = ?", ROW_MAPPER, id, workId);
        return list.stream().findFirst();
    }

    /** 幂等状态机：仅 pending → 目标状态，防重复操作，返回受影响行数 */
    public int updateStatus(long id, long workId, String status) {
        return jdbcTemplate.update(
                "UPDATE pending_conflict SET status = ? WHERE id = ? AND work_id = ? AND status = 'pending'",
                status, id, workId);
    }

    /** 是否已存在同 entity+attribute 的未决冲突（去重用，可选） */
    public boolean existsPending(long workId, String entity, String attribute) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pending_conflict WHERE work_id = ? AND entity = ? AND attribute = ? AND status = 'pending'",
                Long.class, workId, entity, attribute);
        return count != null && count > 0;
    }

    /** 删除该章产生的待确认冲突（无论状态），返回受影响行数 */
    public int deleteByChapter(long workId, int seq) {
        return jdbcTemplate.update(
                "DELETE FROM pending_conflict WHERE work_id = ? AND chapter_no = ?", workId, seq);
    }

    /** 删除作品下全部待确认冲突，返回受影响行数 */
    public int deleteByWorkId(long workId) {
        return jdbcTemplate.update("DELETE FROM pending_conflict WHERE work_id = ?", workId);
    }
}
