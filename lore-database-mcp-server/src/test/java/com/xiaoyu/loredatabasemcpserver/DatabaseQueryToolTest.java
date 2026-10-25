package com.xiaoyu.loredatabasemcpserver;

import com.xiaoyu.loredatabasemcpserver.tool.DatabaseQueryTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseQueryToolTest {

    private final DatabaseQueryTool tool = new DatabaseQueryTool(null);

    @Test
    void validSelect_injectsWorkId() {
        DatabaseQueryTool.SelectPlan plan =
                tool.buildSelectPlan("foreshadow", "content,planted_chapter", "status=open", 42L);
        assertEquals("SELECT content, planted_chapter FROM foreshadow WHERE work_id = ? AND status = ?",
                plan.sql());
        assertEquals(42L, plan.params().get(0));   // 第一个参数必须是 workId
        assertEquals("open", plan.params().get(1));
    }

    @Test
    void emptyFields_defaultsToAllAllowed() {
        DatabaseQueryTool.SelectPlan plan = tool.buildSelectPlan("foreshadow", "", "status=open", 42L);
        assertTrue(plan.sql().startsWith("SELECT "));
        assertTrue(plan.sql().contains(" WHERE work_id = ? AND status = ?"));
    }

    @Test
    void illegalTable_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.buildSelectPlan("users", "id", "", 42L));
        assertThrows(IllegalArgumentException.class,
                () -> tool.buildSelectPlan("foreshadow; DROP TABLE x", "id", "", 42L));
    }

    @Test
    void illegalField_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.buildSelectPlan("foreshadow", "*", "", 42L));
        assertThrows(IllegalArgumentException.class,
                () -> tool.buildSelectPlan("foreshadow", "password", "", 42L));
    }

    @Test
    void workIdCondition_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.buildSelectPlan("foreshadow", "content", "work_id=2", 42L));
    }

    @Test
    void injectionIsTreatedAsValue() {
        DatabaseQueryTool.SelectPlan plan =
                tool.buildSelectPlan("foreshadow", "content", "status=open OR 1=1", 42L);
        assertFalse(plan.sql().contains("OR 1=1"), "注入串绝不能拼进 SQL");
        assertEquals("open OR 1=1", plan.params().get(1)); // 作为普通值走占位符
    }
}
