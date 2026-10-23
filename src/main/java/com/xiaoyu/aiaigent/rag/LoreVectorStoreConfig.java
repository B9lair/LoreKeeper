package com.xiaoyu.aiaigent.rag;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoreVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    // 步骤 1.3：内存向量库切换为 pgvector，此处 @Bean 注释掉，避免与 pgVectorVectorStore 冲突。
    // 重命名后由 LoreVectorStoreConfig 承接（见附录 A）。
//    @Bean
//    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel){
//        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
//                .build();
//        // 加载文档
//        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
//        // 自主切分文档
////        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);
//        // 自动补充关键词元信息
//        List<Document> enrichedDocuments =myKeywordEnricher.enrichDocuments(documents);
//        simpleVectorStore.add(enrichedDocuments);
//        return simpleVectorStore;
//    }
}
