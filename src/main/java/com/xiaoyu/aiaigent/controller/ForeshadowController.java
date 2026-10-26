package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.ForeshadowService;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
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
 * 伏笔管理接口。workId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/foreshadow")
public class ForeshadowController {

    @Resource
    private ForeshadowService foreshadowService;

    public record CreateForeshadowRequest(String content, Integer plantedChapter) {
    }

    public record ResolveForeshadowRequest(Integer resolvedChapter) {
    }

    @GetMapping("/list")
    public List<ForeshadowRepository.Foreshadow> list() {
        return foreshadowService.list(TenantContext.get().workId());
    }

    @PostMapping("/create")
    public ForeshadowRepository.Foreshadow create(@RequestBody CreateForeshadowRequest req) {
        return foreshadowService.create(TenantContext.get().workId(), req.content(), req.plantedChapter());
    }

    @PostMapping("/{id}/resolve")
    public ForeshadowRepository.Foreshadow resolve(@PathVariable long id,
                                                   @RequestBody ResolveForeshadowRequest req) {
        return foreshadowService.resolve(TenantContext.get().workId(), id, req.resolvedChapter());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        foreshadowService.delete(TenantContext.get().workId(), id);
    }
}
