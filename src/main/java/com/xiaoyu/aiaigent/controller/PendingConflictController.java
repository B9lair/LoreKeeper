package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.PendingConflictService;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 待确认队列接口。workId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/pending")
public class PendingConflictController {

    @Resource
    private PendingConflictService pendingConflictService;

    @GetMapping("/list")
    public List<PendingConflictRepository.PendingConflict> list() {
        return pendingConflictService.list(TenantContext.get().workId());
    }

    @PostMapping("/{id}/accept")
    public PendingConflictRepository.PendingConflict accept(@PathVariable long id) {
        return pendingConflictService.accept(TenantContext.get().workId(), id);
    }

    @PostMapping("/{id}/reject")
    public PendingConflictRepository.PendingConflict reject(@PathVariable long id) {
        return pendingConflictService.reject(TenantContext.get().workId(), id);
    }
}
