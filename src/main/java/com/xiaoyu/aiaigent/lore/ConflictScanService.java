package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.HybridSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 改设定冲突扫描（2.6.4，方向与 ConsistencyReviewService 相反）：
 * 拿「改后的设定」当 query 检索章节库 → LLM 判断哪些章节与新设定矛盾 → 返回待修正清单。
 */
@Service
public class ConflictScanService {

    private static final int TOP_K = 5;

    private final HybridSearchService hybridSearchService;
    private final ChatClient chatClient;

    public ConflictScanService(HybridSearchService hybridSearchService, ChatModel dashscopeChatModel) {
        this.hybridSearchService = hybridSearchService;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    private static final String SCAN_SYSTEM_PROMPT = """
            你是小说设定一致性审查员。作者刚把一条设定改为新值，请判断它与下列章节原文是否矛盾（"吃书"）。

            判定标准（严格）：
            - 只有当片段「明确写出」的内容与新设定在事实上互斥时，才判 isConflict=true。
            - 片段未提及该设定、只是措辞不同但可兼容、或属可随时间变化的软信息 → isConflict=false。
            - 不要脑补、不要推断"可能矛盾"。

            只输出一个 JSON 对象：{"items":[{"chapterNo":"3","excerpt":"原文矛盾原句","reason":"矛盾原因","isConflict":true}]}
            chapterNo 用片段标注里的章节号；若片段未标注章节则用 "?"。
            每条候选片段都要给出判定；isConflict=false 的也要放进 items。
            不要输出 JSON 以外的任何内容。""";

    public List<ConflictScanItem> scan(long workId, String entity, String attribute, String value) {
        String query = entity + " " + attribute + " " + value;
        List<Document> docs = hybridSearchService.search(query, workId, TOP_K);

        // ⚠️ HybridSearchService 不区分 type（会混入 setting 块），必须只保留章节
        List<Document> chapters = filterChapterDocs(docs);
        if (chapters.isEmpty()) {
            return List.of();
        }

        ConflictScanResult result = chatClient.prompt()
                .system(SCAN_SYSTEM_PROMPT)
                .user(buildUserPrompt(entity, attribute, value, chapters))
                .call()
                .entity(ConflictScanResult.class);

        if (result == null || result.items() == null) {
            return List.of();
        }
        // 代码层兜底过滤：只保留 LLM 判定的真冲突（反幻觉，不靠 prompt）
        return result.items().stream()
                .filter(i -> i != null && i.isConflict())
                .toList();
    }

    /** 只保留 type=chapter 的文档（package-private 便于单测） */
    static List<Document> filterChapterDocs(List<Document> docs) {
        if (docs == null) {
            return List.of();
        }
        return docs.stream()
                .filter(d -> d != null && "chapter".equals(d.getMetadata().get("type")))
                .toList();
    }

    private String buildUserPrompt(String entity, String attribute, String value, List<Document> chapters) {
        StringBuilder sb = new StringBuilder();
        sb.append("新设定：").append(entity).append(" 的 ").append(attribute).append(" = ").append(value)
                .append("\n\n候选章节片段：\n");
        for (int i = 0; i < chapters.size(); i++) {
            Document d = chapters.get(i);
            Object no = d.getMetadata().get("chapterNo");
            String chapterNo = no == null ? "?" : String.valueOf(no);
            sb.append("[").append(i + 1).append("] 第").append(chapterNo).append("章：")
                    .append(d.getText()).append("\n");
        }
        return sb.toString();
    }
}
