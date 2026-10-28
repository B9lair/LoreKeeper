package com.xiaoyu.aiaigent.controller;

import com.xiaoyu.aiaigent.common.TenantContext;
import com.xiaoyu.aiaigent.lore.CharacterRef;
import com.xiaoyu.aiaigent.lore.CharacterService;
import com.xiaoyu.aiaigent.repository.CharacterRepository;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色档案接口。workId 从登录态取，不信任请求参数。
 */
@RestController
@RequestMapping("/character")
public class CharacterController {

    @Resource
    private CharacterService characterService;

    public record BackfillRequest(List<CharacterRef> characters) {
    }

    public record IgnoreRequest(String entity) {
    }

    @GetMapping("/list")
    public List<CharacterRepository.Character> list() {
        return characterService.list(TenantContext.get().workId());
    }

    /** 回填候选（world_setting 实体名，尚未成为角色且未被忽略） */
    @GetMapping("/candidates")
    public List<String> candidates() {
        return characterService.candidates(TenantContext.get().workId());
    }

    /** 已忽略的候选实体名 */
    @GetMapping("/ignored")
    public List<String> ignored() {
        return characterService.ignored(TenantContext.get().workId());
    }

    /** 人工确认导入角色，返回实际新增数量 */
    @PostMapping("/backfill")
    public int backfill(@RequestBody BackfillRequest req) {
        return characterService.backfill(TenantContext.get().workId(), req.characters());
    }

    /** 忽略候选实体（确认为非人物，从候选隐藏） */
    @PostMapping("/ignore")
    public void ignore(@RequestBody IgnoreRequest req) {
        characterService.ignore(TenantContext.get().workId(), req.entity());
    }

    /** 恢复被忽略的候选实体 */
    @PostMapping("/restore")
    public void restore(@RequestBody IgnoreRequest req) {
        characterService.restore(TenantContext.get().workId(), req.entity());
    }
}
