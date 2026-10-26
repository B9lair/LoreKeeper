package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 伏笔管理（B5）：list / create（含向量化）/ resolve（手动回收，open→closed）/ delete（删表 + 删向量）。
 */
@Service
public class ForeshadowService {

    private static final Logger log = LoggerFactory.getLogger(ForeshadowService.class);

    private final ForeshadowRepository foreshadowRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public ForeshadowService(ForeshadowRepository foreshadowRepository,
                             ChunkingService chunkingService,
                             VectorStore vectorStore,
                             JdbcTemplate jdbcTemplate) {
        this.foreshadowRepository = foreshadowRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ForeshadowRepository.Foreshadow> list(long workId) {
        return foreshadowRepository.findByWorkId(workId);
    }

    public ForeshadowRepository.Foreshadow create(long workId, String content, Integer plantedChapter) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "伏笔内容不能为空");
        }
        String trimmed = content.trim();
        long id = foreshadowRepository.save(workId, trimmed, plantedChapter);
        indexForeshadow(workId, id, plantedChapter, trimmed);
        return foreshadowRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "伏笔创建失败"));
    }

    public ForeshadowRepository.Foreshadow resolve(long workId, long id, Integer resolvedChapter) {
        int rows = foreshadowRepository.markResolved(id, workId, resolvedChapter);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "伏笔不存在或不属于当前作品");
        }
        return foreshadowRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "伏笔查询失败"));
    }

    /** 删除伏笔：先删向量索引，再删事实源表（单事务原子完成） */
    @Transactional
    public void delete(long workId, long id) {
        removeForeshadowIndex(id);
        int rows = foreshadowRepository.deleteById(id, workId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "伏笔不存在或不属于当前作品");
        }
    }

    private void indexForeshadow(long workId, long fid, Integer chapterNo, String content) {
        int no = chapterNo == null ? 0 : chapterNo;
        try {
            vectorStore.add(List.of(
                    chunkingService.settingToDocument(workId, fid, no, "foreshadow", content)));
        } catch (Exception e) {
            log.warn("伏笔向量索引失败（事实源已落库，可后续重建）: {}", e.getMessage());
        }
    }

    /** 删向量索引：type=foreshadow + entityId=foreshadowId（Document.id 是 UUID，只能 jdbcTemplate 直删） */
    private void removeForeshadowIndex(long foreshadowId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata ->> 'type' = 'foreshadow' AND metadata ->> 'entityId' = ?",
                String.valueOf(foreshadowId));
    }
}
