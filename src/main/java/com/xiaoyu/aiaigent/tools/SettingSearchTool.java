package com.xiaoyu.aiaigent.tools;

import com.xiaoyu.aiaigent.rag.HybridSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索设定工具
 * RAG-as-Tool：把混合检索封装成 Agent 可自主调用的 @Tool。
 */
public class SettingSearchTool {

    private final HybridSearchService hybridSearchService;

    private final long workId;

    /** 检索类型白名单（null = 不过滤，三类内容全召回）；统一 Agent 恒传 null，保留硬过滤入口 */
    private final Set<String> types;

    /** 优先类型（null = 无优先级）：优先召回这些类型，不足 topK 再用全量补；统一 Agent 传 setting/foreshadow */
    private final Set<String> preferredTypes;

    public SettingSearchTool(HybridSearchService hybridSearchService, long workId) {
        this(hybridSearchService, workId, null, null);
    }

    public SettingSearchTool(HybridSearchService hybridSearchService, long workId, Set<String> types) {
        this(hybridSearchService, workId, types, null);
    }

    public SettingSearchTool(HybridSearchService hybridSearchService, long workId,
                             Set<String> types, Set<String> preferredTypes) {
        this.hybridSearchService = hybridSearchService;
        this.workId = workId;
        this.types = types;
        this.preferredTypes = preferredTypes;
    }

    @Tool(description = "检索世界观设定库（角色/地点/规则/已写章节），返回最相关设定片段")
    public String settingSearch(@ToolParam(description = "检索查询，如 '女二 在 青阳城 的剧情'") String query) {
        // preferredTypes 非空走分层召回（优先类型 + 不足补全量），否则走硬过滤/全量（向后兼容）
        List<Document> docs = (preferredTypes != null && !preferredTypes.isEmpty())
                ? searchPreferred(query)
                : hybridSearchService.search(query, workId, 3, types);
        // 空结果返回明确提示，避免 LLM 误判为「工具出错」而反复重试
        if (docs.isEmpty()) {
            return "（未检索到与当前作品相关的设定片段）";
        }
        return docs.stream()
                .map(SettingSearchTool::formatHit)
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * 分层召回：优先 preferredTypes 填满 topK，不足再用全量召回补 chapter。
     * 两次 search 各自独立 rerank（组内精排），合并后 preferred 整体在前，实现「类型优先 > 相关性」。
     */
    private List<Document> searchPreferred(String query) {
        int topK = 3;
        List<Document> preferred = hybridSearchService.search(query, workId, topK, preferredTypes);
        if (preferred.size() >= topK) {
            return preferred;
        }
        List<Document> all = hybridSearchService.search(query, workId, topK, null);
        // 两次 search 都来自 vector_store 的同一 id（UUID）列，按 id 去重可靠（RrfMerger 亦用它去重）
        Set<String> seen = preferred.stream().map(Document::getId).collect(Collectors.toSet());
        List<Document> fill = all.stream()
                .filter(d -> d.getId() == null || !seen.contains(d.getId()))
                .toList();
        List<Document> merged = new ArrayList<>(preferred);
        merged.addAll(fill);
        return merged.stream().limit(topK).toList();
    }

    /** 命中结果带「类型 + 章节」标注，让 LLM 能注明出处、区分设定/章节/伏笔（而非裸文本） */
    private static String formatHit(Document d) {
        String type = str(d.getMetadata(), "type");
        String typeLabel;
        if (type == null || type.isBlank()) {
            typeLabel = "片段";
        } else {
            typeLabel = switch (type) {
                case "chapter" -> "章节";
                case "setting" -> "设定";
                case "foreshadow" -> "伏笔";
                default -> type;
            };
        }
        String chapterNo = str(d.getMetadata(), "chapterNo");
        String source = (chapterNo == null || chapterNo.isBlank())
                ? typeLabel
                : typeLabel + "|第" + chapterNo + "章";
        return "[" + source + "] " + d.getText();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
