package com.xiaoyu.aiaigent.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 分块策略
 * - 章节：父子分块，段落子块向量化，parentId 关联 chapter.id（回表取整章原文）
 * - 设定/伏笔：一条 = 一块，绝不切碎，entityId 关联回表 id
 * metadata 统一带 type/workId（字符串），供检索强制过滤。
 */
@Component
public class ChunkingService {

    /** 章节 → 段落子块（先按空行/逻辑段切） */
    public List<Document> chunkChapter(long workId, long chapterId, int chapterNo, String chapterText) {
        String wid = String.valueOf(workId);
        String parentId = String.valueOf(chapterId);   
        return splitByParagraph(chapterText).stream()
                .map(p -> new Document(p, Map.of(
                        "type", "chapter",
                        "workId", wid,                 // 字符串
                        "chapterNo", chapterNo,
                        "parentId", parentId)))
                .toList();
    }

    /** 设定/伏笔 → 独立成块（type 为 "setting" 或 "foreshadow"） */
    public Document settingToDocument(long workId, long entityId, int chapterNo,
                                      String type, String text) {
        return new Document(text, Map.of(
                "type", type,
                "workId", String.valueOf(workId),
                "entityId", String.valueOf(entityId), // 关联 world_setting.id / foreshadow.id
                "chapterNo", chapterNo));
    }

    private List<String> splitByParagraph(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}