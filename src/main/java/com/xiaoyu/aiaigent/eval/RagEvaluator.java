package com.xiaoyu.aiaigent.eval;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG 检索评测器：算 Recall@K 与 MRR（Mean Reciprocal Rank）。
 * <p>
 * 纯函数、无状态，不碰 DB / 向量库 / LLM，可直接单测（对应方案 4.1 L4「自研打分」）。
 * 命中判定以「设定条目 id」为准：从 Document 的 metadata.entityId 提取（对应 world_setting.id），
 * 而非 pgvector 自动生成的 Document.id。
 */
public class RagEvaluator {

    /** 一个检索样本：query + 期望命中的设定条目 id 集合 */
    public record RagSample(String query, List<String> expectedIds) {
    }

    /** 评测结果：Recall@K + MRR */
    public record RagMetrics(int k, double recallAtK, double mrr, int queryCount) {
        @Override
        public String toString() {
            return String.format("Recall@%d=%.4f, MRR=%.4f (queries=%d)", k, recallAtK, mrr, queryCount);
        }
    }

    /**
     * 从检索返回的 Document 列表提取「命中顺序的 entityId 列表」。
     * entityId 存的是字符串，这里统一 String.valueOf 归一，避免类型不一致导致比对落空。
     */
    public static List<String> entityIdsOf(List<Document> docs) {
        if (docs == null) {
            return List.of();
        }
        return docs.stream().map(RagEvaluator::entityIdOf).toList();
    }

    private static String entityIdOf(Document d) {
        if (d == null || d.getMetadata() == null) {
            return null;
        }
        Object v = d.getMetadata().get("entityId");
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 计算 Recall@K 与 MRR。
     *
     * @param samples      评测样本（与 retrievedIds 按下标一一对应）
     * @param retrievedIds 每个 query 的检索命中 entityId 顺序列表
     * @param k            取前 K 个结果
     */
    public RagMetrics evaluate(List<RagSample> samples, List<List<String>> retrievedIds, int k) {
        if (samples == null || samples.isEmpty()) {
            return new RagMetrics(k, 0.0, 0.0, 0);
        }
        if (retrievedIds == null || retrievedIds.size() != samples.size()) {
            throw new IllegalArgumentException("retrievedIds 数量必须与 samples 一致");
        }

        double recallSum = 0.0;
        double rrSum = 0.0;

        for (int i = 0; i < samples.size(); i++) {
            RagSample sample = samples.get(i);
            List<String> hits = retrievedIds.get(i) == null ? List.of() : retrievedIds.get(i);
            List<String> expected = sample.expectedIds() == null ? List.of() : sample.expectedIds();

            List<String> topK = hits.stream().limit(k).toList();

            // Recall@K：前 K 个结果命中期望条目数 / 该 query 总期望条目数
            long hitCount = topK.stream().filter(expected::contains).count();
            if (!expected.isEmpty()) {
                recallSum += (double) hitCount / expected.size();
            }

            // MRR：第一个命中期望条目的排名倒数，未命中记 0
            int firstRank = 0;
            for (int rank = 0; rank < topK.size(); rank++) {
                if (expected.contains(topK.get(rank))) {
                    firstRank = rank + 1;
                    break;
                }
            }
            rrSum += firstRank == 0 ? 0.0 : 1.0 / firstRank;
        }

        int n = samples.size();
        return new RagMetrics(k, recallSum / n, rrSum / n, n);
    }

    /**
     * 计算 Context Precision@K：前 K 个检索结果中命中期望条目的比例（"检索纯度"）。
     * 与 Recall@K（覆盖率）互补：Recall 看"证据找全没"，Precision 看"带回的噪声多不多"。
     * 分母取实际前 K 结果数（返回不足 K 时不人为拉低纯度）。
     */
    public double contextPrecisionAtK(List<String> retrievedIds, List<String> expectedIds, int k) {
        if (retrievedIds == null || retrievedIds.isEmpty()) {
            return 0.0;
        }
        List<String> topK = retrievedIds.stream().limit(k).toList();
        if (topK.isEmpty()) {
            return 0.0;
        }
        List<String> expected = expectedIds == null ? List.of() : expectedIds;
        long hit = topK.stream().filter(expected::contains).count();
        return (double) hit / topK.size();
    }
}
