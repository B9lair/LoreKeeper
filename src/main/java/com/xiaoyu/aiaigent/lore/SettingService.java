package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository.WorldSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 设定管理（B4）：list / save（新建或更新 + 冲突扫描 + 重建向量索引）/ delete。
 *
 * ⚠️ 关键点（2.6.5 落地）：
 * - 设定事实源在 world_setting 表，向量库只存检索索引（type=setting，entityId 回表）。
 * - 当前代码里设定从未向量化（settingToDocument 只被伏笔用过），save 必须补 add。
 * - Document.id 是 UUID（add 时自动生成），无法按 entityId 调 vectorStore.delete，
 *   删除索引必须 jdbcTemplate 直删 vector_store。
 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private final WorldSettingRepository worldSettingRepository;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ConflictScanService conflictScanService;

    public SettingService(WorldSettingRepository worldSettingRepository,
                          ChunkingService chunkingService,
                          VectorStore vectorStore,
                          JdbcTemplate jdbcTemplate,
                          ConflictScanService conflictScanService) {
        this.worldSettingRepository = worldSettingRepository;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.conflictScanService = conflictScanService;
    }

    public List<WorldSetting> list(long workId) {
        return worldSettingRepository.findByWorkId(workId);
    }

    public SettingSaveResult save(long workId, SettingSaveRequest req) {
        if (isBlank(req.entity()) || isBlank(req.attribute()) || isBlank(req.value())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entity/attribute/value 不能为空");
        }
        String entity = req.entity().trim();
        String attribute = req.attribute().trim();
        String value = req.value().trim();
        String type = isBlank(req.type()) ? "rule" : req.type().trim();
        String content = entity + " 的 " + attribute + "：" + value;

        long id;
        int version;
        if (req.id() == null) {
            // 新建：version = 该实体下一个版本号
            version = worldSettingRepository.nextVersion(workId, entity);
            WorldSetting ws = worldSettingRepository.save(WorldSetting.of(
                    workId, type, content, entity, attribute, value,
                    req.evidence(), req.establishedChapter(), version));
            id = ws.id();
        } else {
            // 更新：覆盖式 + version+1（update SQL 已带 WHERE id AND work_id 防跨作品）
            WorldSetting old = worldSettingRepository.findById(req.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "设定不存在"));
            version = (old.version() == null ? 0 : old.version()) + 1;
            WorldSetting ws = new WorldSetting(old.id(), workId, type, content, entity, attribute, value,
                    req.evidence(), req.establishedChapter(), version);
            int rows = worldSettingRepository.update(ws);
            if (rows == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设定不存在或不属于当前作品");
            }
            id = ws.id();
        }

        reindexSetting(workId, id, req.establishedChapter(), content);

        List<ConflictScanItem> conflicts = Boolean.FALSE.equals(req.scanConflict())
                ? List.of()
                : conflictScanService.scan(workId, entity, attribute, value);

        return new SettingSaveResult(id, version, conflicts);
    }

    public void delete(long workId, long id) {
        int rows = worldSettingRepository.deleteById(id, workId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设定不存在或不属于当前作品");
        }
        removeSettingIndex(id);
    }

    /**
     * 采纳冲突新值：更新既有设定 + 重建向量索引（不做冲突扫描，人工已裁决）。
     * 供 B6 待确认队列 accept 复用，避免重复索引维护逻辑。
     */
    public WorldSetting applyValue(long workId, String entity, String attribute,
                                   String newValue, String evidence, Integer chapterNo) {
        WorldSetting ws = worldSettingRepository.findByEntityAndAttribute(workId, entity, attribute)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "设定不存在"));
        int version = (ws.version() == null ? 0 : ws.version()) + 1;
        String content = entity + " 的 " + attribute + "：" + newValue;
        WorldSetting updated = new WorldSetting(ws.id(), workId, ws.type(), content,
                entity, attribute, newValue, evidence, chapterNo, version);
        int rows = worldSettingRepository.update(updated);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "设定不存在或不属于当前作品");
        }
        reindexSetting(workId, ws.id(), chapterNo, content);
        return updated;
    }

    /** 重建索引：先删旧索引，再写新索引 */
    private void reindexSetting(long workId, long settingId, Integer chapterNo, String content) {
        removeSettingIndex(settingId);
        int no = chapterNo == null ? 0 : chapterNo;
        try {
            vectorStore.add(List.of(
                    chunkingService.settingToDocument(workId, settingId, no, "setting", content)));
        } catch (Exception e) {
            log.warn("设定向量索引失败（事实源已落库，可后续重建）workId={} settingId={}: {}",
                    workId, settingId, e.getMessage());
        }
    }

    /** 删除索引：按 entityId + type 精确删除（entityId 存的是字符串） */
    private void removeSettingIndex(long settingId) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata ->> 'type' = 'setting' AND metadata ->> 'entityId' = ?",
                String.valueOf(settingId));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
