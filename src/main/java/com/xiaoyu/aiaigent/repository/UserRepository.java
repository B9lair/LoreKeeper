package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户表 CRUD（users）。
 */
@Repository
public class UserRepository {

    public record User(long id, String email, String passwordHash) {
    }

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash")
    );

    public Optional<User> findByEmail(String email) {
        List<User> list = jdbcTemplate.query(
                "SELECT id, email, password_hash FROM users WHERE email = ?", ROW_MAPPER, email);
        return list.stream().findFirst();
    }

    /** 按主键查用户（3.2 发邮件时按 userId 取收件邮箱） */
    public Optional<User> findById(long id) {
        List<User> list = jdbcTemplate.query(
                "SELECT id, email, password_hash FROM users WHERE id = ?", ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /** 新增用户，返回自增 id（PostgreSQL RETURNING） */
    public long save(String email, String passwordHash) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id",
                Long.class, email, passwordHash);
    }
}
