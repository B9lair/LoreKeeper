package com.xiaoyu.aiaigent.rag;

import org.springframework.ai.document.Document;
import java.util.List;

public interface RerankService {
    /** 按 query 对候选 docs 重排，返回按相关性降序的列表 */
    List<Document> rerank(String query, List<Document> docs);
}