package com.xiaoyu.aiaigent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评测 trace 落库（eval_trace）：记录线上真实问答的工具序列 / 回答 / loopGuard / 步数 / 耗时，
 * 供阶段三「线上失败样本回灌」挑选坏样本（运营侧人工抽检 → 回灌 dataset.json 的 badCases）。
 * <p>
 * insert-only 日志表，无 update/delete；created_at 由数据库 DEFAULT now() 生成，不在此维护。
 */
@Repository
public class EvalTraceRepository {

    /** eval_trace 实体 */
    public record EvalTrace(
            Long workId,
            String userMessage,
            String toolSequence,   // JSON 数组字符串，如 ["settingSearch","doTerminate"]
            String answer,
            boolean loopGuard,
            int stepCount,
            long costMs
    ) {
    }

    private final JdbcTemplate jdbcTemplate;

    public EvalTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 落一条 trace。 */
    public void save(EvalTrace t) {
        jdbcTemplate.update(
                "INSERT INTO eval_trace (work_id, user_message, tool_sequence, answer, loop_guard, step_count, cost_ms) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                t.workId(), t.userMessage(), t.toolSequence(), t.answer(),
                t.loopGuard(), t.stepCount(), t.costMs());
    }

    /** 取某作品最近 N 条 trace（运营回灌挑坏样本用）。 */
    public List<EvalTrace> findRecent(long workId, int limit) {
        return jdbcTemplate.query(
                "SELECT work_id, user_message, tool_sequence, answer, loop_guard, step_count, cost_ms "
                        + "FROM eval_trace WHERE work_id = ? ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new EvalTrace(
                        rs.getLong("work_id"),
                        rs.getString("user_message"),
                        rs.getString("tool_sequence"),
                        rs.getString("answer"),
                        rs.getBoolean("loop_guard"),
                        rs.getInt("step_count"),
                        rs.getLong("cost_ms")),
                workId, limit);
    }
}
