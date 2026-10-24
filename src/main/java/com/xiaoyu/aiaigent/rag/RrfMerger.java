package com.xiaoyu.aiaigent.rag;

import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RRF（Reciprocal Rank Fusion）融合器：按排名倒数融合多路检索结果。
 * 独立成类，纯函数、无状态，可直接单测。
 */
public class RrfMerger {

    private final double k;

    public RrfMerger(double k) {
        this.k = k;
    }

    /** 默认 k=60 */
    public static RrfMerger withDefaultK() {
        return new RrfMerger(60.0);
    }

    /** 对多路结果做 RRF 融合，返回按分数降序、按 Document.id 去重后的结果 */
    @SafeVarargs
    public final List<Document> merge(List<Document>... lists) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Document> byId = new LinkedHashMap<>();
        for (List<Document> list : lists) {
            if (list == null) {
                continue;
            }
            int rank = 1;
            for (Document d : list) {
                add(d, rank++, scores, byId);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> byId.get(e.getKey()))
                .collect(Collectors.toList());
    }

    private void add(Document d, int rank, Map<String, Double> scores, Map<String, Document> byId) {
        if (d == null || d.getId() == null) {
            return;
        }
        byId.putIfAbsent(d.getId(), d);
        scores.merge(d.getId(), 1.0 / (k + rank), Double::sum);
    }
}