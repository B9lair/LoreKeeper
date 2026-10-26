package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.WorkService;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作品管理接口。userId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/work")
public class WorkController {

    @Resource
    private WorkService workService;

    public record CreateWorkRequest(String title, String genre) {
    }

    public record SwitchWorkRequest(long targetWorkId) {
    }

    @GetMapping("/list")
    public List<WorkRepository.Work> list() {
        return workService.list(TenantContext.get().userId());
    }

    @PostMapping("/create")
    public WorkRepository.Work create(@RequestBody CreateWorkRequest req) {
        return workService.create(TenantContext.get().userId(), req.title(), req.genre());
    }

    @PostMapping("/switch")
    public WorkService.SwitchResult switchWork(@RequestBody SwitchWorkRequest req) {
        return workService.switchWork(TenantContext.get().userId(), req.targetWorkId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        workService.delete(TenantContext.get().userId(), id);
    }
}
