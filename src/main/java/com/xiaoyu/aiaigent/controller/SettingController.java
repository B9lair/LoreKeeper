package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.SettingSaveRequest;
import com.xiaoyu.aiaigent.lore.SettingSaveResult;
import com.xiaoyu.aiaigent.lore.SettingService;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
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
 * 设定管理接口。workId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/setting")
public class SettingController {

    @Resource
    private SettingService settingService;

    @GetMapping("/list")
    public List<WorldSettingRepository.WorldSetting> list() {
        return settingService.list(TenantContext.get().workId());
    }

    @PostMapping("/save")
    public SettingSaveResult save(@RequestBody SettingSaveRequest req) {
        return settingService.save(TenantContext.get().workId(), req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        settingService.delete(TenantContext.get().workId(), id);
    }
}
