package com.xiaoyu.aiaigent.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Set;

/**
 * 评测集数据模型（对应 src/test/resources/eval/dataset.json）。
 * <p>
 * - seed：统一测试语料（种子数据），既构造评测集又做演示
 * - rag / consistency / extraction：三类 golden 样本
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalDataset(
        Seed seed,
        List<RagCase> rag,
        List<ConsistencyCase> consistency,
        List<ExtractionCase> extraction
) {

    /** 种子数据：设定条目 + 角色（entityMatch 提权依赖 character 表） */
    public record Seed(List<SettingSeed> settings, List<CharacterSeed> characters) {
    }

    /** 设定条目：id 对应向量库 metadata.entityId（字符串） */
    public record SettingSeed(String id, String type, int chapterNo, String text) {
    }

    /** 角色：name + aliases（进 character 表，供 HybridSearchService.entityMatch 提权） */
    public record CharacterSeed(String name, List<String> aliases) {
    }

    /** RAG 检索样本 */
    public record RagCase(String query, List<String> expectedIds) {
    }

    /** 一致性检测样本：同一实体同一属性，valueA=历史断言，valueB=候选断言 */
    public record ConsistencyCase(String entity, String attribute, String valueA, String valueB, boolean isConflict) {
    }

    /** 实体抽取样本 */
    public record ExtractionCase(String text, Set<String> expectedEntities, int expectedForeshadowCount) {
    }
}
