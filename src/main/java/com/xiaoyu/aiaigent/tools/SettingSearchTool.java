package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.HybridSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索设定工具
 * RAG-as-Tool：把混合检索封装成 Agent 可自主调用的 @Tool。
 */
public class SettingSearchTool {

    private final HybridSearchService hybridSearchService;

    private final long workId;

    public SettingSearchTool(HybridSearchService hybridSearchService, long workId) {
        this.hybridSearchService = hybridSearchService;
        this.workId = workId;
    }

    @Tool(description = "检索世界观设定库（角色/地点/规则/已写章节），返回最相关设定片段")
    public String settingSearch(@ToolParam(description = "检索查询，如 '女二 在 青阳城 的剧情'") String query) {
        List<Document> docs = hybridSearchService.search(query, workId, 3);
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
}
