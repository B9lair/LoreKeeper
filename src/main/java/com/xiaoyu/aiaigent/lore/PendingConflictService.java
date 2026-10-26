package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository.PendingConflict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 待确认队列（B6，2.6.6 人机协同）：list / accept（采纳新值）/ reject（驳回）。
 * accept 委托 SettingService.applyValue 更新设定 + 重建索引（复用 B4 的索引维护逻辑）。
 */
@Service
public class PendingConflictService {

    private final PendingConflictRepository pendingConflictRepository;
    private final SettingService settingService;

    public PendingConflictService(PendingConflictRepository pendingConflictRepository,
                                  SettingService settingService) {
        this.pendingConflictRepository = pendingConflictRepository;
        this.settingService = settingService;
    }

    public List<PendingConflict> list(long workId) {
        return pendingConflictRepository.findByWorkId(workId, "pending");
    }

    public PendingConflict accept(long workId, long id) {
        PendingConflict pc = findPending(workId, id);
        // 采纳新值：更新 world_setting + 重建向量索引（不触发冲突扫描，人工已裁决）
        settingService.applyValue(workId, pc.entity(), pc.attribute(), pc.newValue(), pc.evidence(), pc.chapterNo());
        int rows = pendingConflictRepository.updateStatus(id, workId, "accepted");
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该冲突已被处理");
        }
        return pendingConflictRepository.findById(id, workId).orElseThrow();
    }

    public PendingConflict reject(long workId, long id) {
        findPending(workId, id);
        int rows = pendingConflictRepository.updateStatus(id, workId, "rejected");
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该冲突已被处理");
        }
        return pendingConflictRepository.findById(id, workId).orElseThrow();
    }

    /** 取一条未决冲突，非 pending 或不存在则抛异常 */
    private PendingConflict findPending(long workId, long id) {
        PendingConflict pc = pendingConflictRepository.findById(id, workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "待确认冲突不存在"));
        if (!"pending".equals(pc.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该冲突已被处理");
        }
        return pc;
    }
}
