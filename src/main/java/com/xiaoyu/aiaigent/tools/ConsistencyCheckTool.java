package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.lore.ConsistencyReport;
import com.xiaoyu.aiaigent.lore.ConsistencyReviewService;
import com.xiaoyu.aiaigent.lore.Conflict;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 设定一致性检测工具（RAG-as-Tool 的姊妹：审查-as-Tool）。
 *
 * 把确定性一致性审查封装成 Agent 可自主调用的 @Tool：
 * 用户粘贴新写章节正文 → 走 {@link ConsistencyReviewService#reviewChapterDryRun} 只读审查
 * （LLM 抽取断言 → 精确归并比对 → 输出矛盾清单 + 未回收伏笔），结果返回给 LLM 转述。
 * 判定完全走确定性逻辑，Agent 只负责触发与转述，不参与判定（审查结论可复现）。
 */
public class ConsistencyCheckTool {

    private final ConsistencyReviewService consistencyReviewService;

    private final long workId;

    public ConsistencyCheckTool(ConsistencyReviewService consistencyReviewService, long workId) {
        this.consistencyReviewService = consistencyReviewService;
        this.workId = workId;
    }

    @Tool(name = "consistencyCheck",
          description = "设定一致性检测：检查用户新写的章节正文是否与既有设定矛盾（吃书）。只读检测，不会写入知识库")
    public String consistencyCheck(
            @ToolParam(description = "新写章节的完整正文文本") String chapterText) {
        if (chapterText == null || chapterText.isBlank()) {
            return "章节文本为空，无法检测";
        }
        ConsistencyReport report = consistencyReviewService.reviewChapterDryRun(workId, null, chapterText);
        return format(report);
    }

    /** 把审查结果格式化成 LLM 易读文本，便于 Agent 据此组织回答 */
    private String format(ConsistencyReport report) {
        List<Conflict> conflicts = report.conflicts();
        StringBuilder sb = new StringBuilder();

        if (conflicts == null || conflicts.isEmpty()) {
            sb.append("一致性检测通过：未发现与既有设定矛盾的断言。");
        } else {
            sb.append("发现 ").append(conflicts.size()).append(" 处设定矛盾：");
            for (int i = 0; i < conflicts.size(); i++) {
                Conflict c = conflicts.get(i);
                sb.append("\n").append(i + 1).append(". 【").append(c.entity()).append("】")
                        .append("新章节写：").append(c.descA())
                        .append("；既有设定：").append(c.descB())
                        .append("（").append(formatChapter(c.chapterB())).append("）");
            }
        }

        List<String> reminders = report.foreshadowReminders();
        if (reminders != null && !reminders.isEmpty()) {
            sb.append("\n\n另有 ").append(reminders.size()).append(" 条未回收伏笔可关注：");
            for (int i = 0; i < reminders.size(); i++) {
                sb.append("\n").append(i + 1).append(". ").append(reminders.get(i));
            }
        }
        return sb.toString();
    }

    private String formatChapter(String chapterNo) {
        return (chapterNo == null || chapterNo.isBlank() || "?".equals(chapterNo))
                ? "历史出处未知"
                : "确立于第" + chapterNo + "章";
    }
}
