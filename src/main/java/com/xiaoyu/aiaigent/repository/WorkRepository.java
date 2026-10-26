package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 作品表 CRUD（work）。
 */
@Repository
public class WorkRepository {

    /**
     * 作品实体，对应 work 表。
     */
    public record Work(Long id, Long userId, String title, String genre) {
    }

    private final JdbcTemplate jdbcTemplate;

    public WorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Work> ROW_MAPPER = (rs, rowNum) -> new Work(
            rs.getObject("id", Long.class),
            rs.getObject("user_id", Long.class),
            rs.getString("title"),
            rs.getString("genre")
    );

    /** 注册时为每个用户创建默认作品，返回自增 id（PostgreSQL RETURNING） */
    public long createDefault(long userId, String title) {
        return create(userId, title, null);
    }

    /** 新建作品（可选 genre），返回自增 id（PostgreSQL RETURNING） */
    public long create(long userId, String title, String genre) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO work (user_id, title, genre) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, title, genre);
    }

    /** 取用户的默认作品（最早创建的那个） */
    public Optional<Long> findDefaultByUserId(long userId) {
        List<Long> list = jdbcTemplate.query(
                "SELECT id FROM work WHERE user_id = ? ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), userId);
        return list.stream().findFirst();
    }

    /** 列出某用户的全部作品（作品管理页） */
    public List<Work> findByUserId(long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM work WHERE user_id = ? ORDER BY id",
                ROW_MAPPER, userId);
    }

    public Optional<Work> findById(long id) {
        List<Work> list = jdbcTemplate.query(
                "SELECT * FROM work WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 删除作品（按 id + user_id 定位，防越权删他人作品），返回受影响行数 */
    public int deleteById(long id, long userId) {
        return jdbcTemplate.update("DELETE FROM work WHERE id = ? AND user_id = ?", id, userId);
    }
}
