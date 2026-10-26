package com.xiaoyu.aiaigent.rag;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DashScope gte-rerank-v2 重排。
 * 基于 dashscope-sdk-java 原生 TextReRank
 * - 参数：query / document(...)（可多次追加）/ topN / returnDocuments
 * - 结果：result.getOutput().getResults() → getIndex() + getRelevanceScore()
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.rerank.enabled", havingValue = "true")
public class DashScopeRerankService implements RerankService {

    private final String apiKey;

    public DashScopeRerankService(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<Document> rerank(String query, List<Document> docs) {
        if (docs == null || docs.size() <= 1) {
            return docs;
        }
        try {
            TextReRank rerank = new TextReRank();
            var builder = TextReRankParam.builder()
                    .apiKey(apiKey)
                    // 配置模型 gte-rerank-v2
                    .model(TextReRank.Models.GTE_RERANK_V2)
                    .query(query)
                    .topN(Math.min(docs.size(), 10))
                    .returnDocuments(false);
            for (Document d : docs) {
                builder.document(d.getText());
            }
            TextReRankResult result = rerank.call(builder.build());

            List<Document> reranked = new ArrayList<>();
            for (var r : result.getOutput().getResults()) {
                int idx = r.getIndex();
                if (idx >= 0 && idx < docs.size()) {
                    reranked.add(docs.get(idx));
                }
            }
            List<Document> out = reranked.isEmpty() ? docs : reranked;
            log.info("rerank 完成：候选 {} 条 -> 重排 {} 条", docs.size(), out.size());
            return out;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.warn("rerank 调用失败，降级为不重排：{}", e.getMessage());
            return docs;
        }
    }
}