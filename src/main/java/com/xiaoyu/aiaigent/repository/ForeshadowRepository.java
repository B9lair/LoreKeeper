package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 伏笔表查询
 */
@Repository
public class ForeshadowRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForeshadowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 查未回收伏笔内容（一致性审查报告的提醒项） */
    public List<String> findOpenContentsByWorkId(long workId) {
        return jdbcTemplate.queryForList(
                "SELECT content FROM foreshadow WHERE work_id = ? AND status = 'open' ORDER BY id",
                String.class, workId);
    }
}
