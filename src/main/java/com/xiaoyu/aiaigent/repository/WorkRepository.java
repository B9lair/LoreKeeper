package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 作品表 CRUD（work）。
 */
@Repository
public class WorkRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 注册时为每个用户创建默认作品，返回自增 id（PostgreSQL RETURNING） */
    public long createDefault(long userId, String title) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO work (user_id, title) VALUES (?, ?) RETURNING id",
                Long.class, userId, title);
    }

    /** 取用户的默认作品（最早创建的那个） */
    public Optional<Long> findDefaultByUserId(long userId) {
        List<Long> list = jdbcTemplate.query(
                "SELECT id FROM work WHERE user_id = ? ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), userId);
        return list.stream().findFirst();
    }
}
