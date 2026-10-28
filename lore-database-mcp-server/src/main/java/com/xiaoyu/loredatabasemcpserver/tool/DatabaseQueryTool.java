package com.xiaoyu.loredatabasemcpserver.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 结构化数据只读查询工具。
 *
 * 安全模型（方案 2.7 / 4.3）：
 * - 模型只能决定「查哪张表、哪几个字段、什么等值条件」，永远写不了 SQL；
 * - SQL 骨架与 WHERE work_id = ? 完全由服务端拼，字段/表走白名单，杜绝注入与跨作品越权；
 * - workId 由客户端（主项目，可信）在调用前强制注入，模型无法伪造。
 */
@Service
public class DatabaseQueryTool {

    /** 表白名单：只读这 4 张业务表（users/work/chapter/consistency_report 一律不可查） */
    private static final Set<String> ALLOWED_TABLES =
            Set.of("character", "world_setting", "timeline_event", "foreshadow");

    /** 字段白名单（每表），与 schema.sql 列对齐 */
    private static final Map<String, Set<String>> ALLOWED_FIELDS = Map.of(
            "character", Set.of("id", "work_id", "name", "aliases", "role", "ability",
                    "first_appear_chapter", "status", "created_at"),
            "world_setting", Set.of("id", "work_id", "type", "content", "entity", "attribute",
                    "value", "evidence", "established_chapter", "version", "created_at"),
            "timeline_event", Set.of("id", "work_id", "time_point", "event", "involved_characters",
                    "chapter", "created_at"),
            "foreshadow", Set.of("id", "work_id", "content", "planted_chapter",
                    "resolved_chapter", "status", "created_at")
    );

    /** 元数据字段：默认查询时排除，避免把租户键/时间戳等对回答无用的信息塞给 LLM */
    private static final Set<String> META_FIELDS = Set.of("work_id", "created_at");

    /** 单次最多返回行数，防止查全表返回巨量数据 */
    private static final int MAX_ROWS = 20;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseQueryTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(name = "query_database",
          description = """
                  精确查询作品结构化数据（等值匹配，非语义检索）。适用：按状态过滤（如未回收伏笔 status=open）、查时间线、按实体名/属性精确查设定。
                  可查表及常用字段：
                  - character: name, aliases, role, ability, first_appear_chapter, status
                  - world_setting: type, entity, attribute, value, evidence, established_chapter, version
                  - timeline_event: time_point, event, involved_characters, chapter
                  - foreshadow: content, planted_chapter, resolved_chapter, status""")
    public String queryDatabase(
            @ToolParam(description = "表名，只能是 character|world_setting|timeline_event|foreshadow 之一") String table,
            @ToolParam(description = "返回字段，逗号分隔（留空查默认常用字段）") String fields,
            @ToolParam(description = "等值过滤条件，逗号分隔 key=value，如 status=open,entity=林玄") String conditions,
            @ToolParam(description = "作品 ID（系统自动注入，请勿填写）") long workId) {
        try {
            SelectPlan plan = buildSelectPlan(table, fields, conditions, workId);
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(plan.sql() + " LIMIT " + MAX_ROWS, plan.params().toArray());
            return formatRows(table, plan.fields(), rows);
        }
        catch (IllegalArgumentException e) {
            // 非法输入：返回明确提示而非抛异常，让 Agent 看到并可自我纠正
            return "查询被拒绝：" + e.getMessage();
        }
    }

    /** 拼装 SQL 骨架（纯函数，便于单测直接断言安全逻辑） */
    public SelectPlan buildSelectPlan(String table, String fields, String conditions, long workId) {
        if (table == null || !ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("非法表名：" + table + "（允许：" + ALLOWED_TABLES + "）");
        }
        Set<String> allowed = ALLOWED_FIELDS.get(table);

        List<String> selected = parseFields(fields);
        if (selected.isEmpty()) {
            // 留空默认查常用字段（排除 work_id/created_at 等元数据）
            selected = allowed.stream().filter(f -> !META_FIELDS.contains(f)).toList();
        }
        for (String f : selected) {
            if (!allowed.contains(f)) {
                throw new IllegalArgumentException("字段不在白名单：" + f);
            }
        }

        List<Object> params = new ArrayList<>();
        params.add(workId); // 强制注入，绝不来自模型

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", selected))
                .append(" FROM ").append(table)
                .append(" WHERE work_id = ?");

        for (String[] kv : parseConditions(conditions)) {
            String key = kv[0];
            String value = kv[1];
            if ("work_id".equalsIgnoreCase(key)) {
                throw new IllegalArgumentException("work_id 由系统注入，不允许作为过滤条件");
            }
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("过滤字段不在白名单：" + key);
            }
            sql.append(" AND ").append(key).append(" = ?");
            params.add(value);
        }
        return new SelectPlan(sql.toString(), params, selected);
    }

    /**
     * 把查询结果格式化成 LLM 易读文本：带表名与命中条数、按字段顺序输出、null 显示为「无」。
     * 相比裸 rows.toString()，去掉了元数据字段，信息密度更高。
     */
    private String formatRows(String table, List<String> fields, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "表 " + table + " 无匹配记录";
        }
        StringBuilder sb = new StringBuilder()
                .append("表 ").append(table).append("，命中 ").append(rows.size()).append(" 条");
        if (rows.size() >= MAX_ROWS) {
            sb.append("（已达上限 ").append(MAX_ROWS).append(" 条，可用 conditions 收窄范围）");
        }
        sb.append("：");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            sb.append("\n").append(i + 1).append(". ");
            sb.append(fields.stream()
                    .map(f -> f + "=" + formatValue(row.get(f)))
                    .collect(Collectors.joining(" | ")));
        }
        return sb.toString();
    }

    private String formatValue(Object v) {
        return v == null ? "无" : String.valueOf(v);
    }

    private List<String> parseFields(String fields) {
        if (fields == null || fields.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String[]> parseConditions(String conditions) {
        if (conditions == null || conditions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(conditions.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    int idx = s.indexOf('=');
                    if (idx <= 0) {
                        throw new IllegalArgumentException("条件格式应为 key=value：" + s);
                    }
                    return new String[]{s.substring(0, idx).trim(), s.substring(idx + 1).trim()};
                })
                .toList();
    }

    public record SelectPlan(String sql, List<Object> params, List<String> fields) {
    }
}
