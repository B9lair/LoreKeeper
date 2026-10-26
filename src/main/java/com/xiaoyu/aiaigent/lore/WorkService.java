package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ChapterRepository;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.TimelineEventRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import com.xiaoyu.aiaigent.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 作品管理（B3）：list / create / switch / delete。
 * workId 归属校验是安全关键——切换/删除他人作品必须 403。
 */
@Service
public class WorkService {

    private static final Logger log = LoggerFactory.getLogger(WorkService.class);

    private final WorkRepository workRepository;
    private final ChapterRepository chapterRepository;
    private final WorldSettingRepository worldSettingRepository;
    private final ForeshadowRepository foreshadowRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final PendingConflictRepository pendingConflictRepository;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public WorkService(WorkRepository workRepository,
                       ChapterRepository chapterRepository,
                       WorldSettingRepository worldSettingRepository,
                       ForeshadowRepository foreshadowRepository,
                       TimelineEventRepository timelineEventRepository,
                       PendingConflictRepository pendingConflictRepository,
                       JwtService jwtService,
                       JdbcTemplate jdbcTemplate) {
        this.workRepository = workRepository;
        this.chapterRepository = chapterRepository;
        this.worldSettingRepository = worldSettingRepository;
        this.foreshadowRepository = foreshadowRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.pendingConflictRepository = pendingConflictRepository;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 列出当前用户的全部作品 */
    public List<WorkRepository.Work> list(long userId) {
        return workRepository.findByUserId(userId);
    }

    /** 新建作品，返回完整作品记录 */
    public WorkRepository.Work create(long userId, String title, String genre) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "作品标题不能为空");
        }
        long id = workRepository.create(userId, title, genre);
        return workRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "作品创建失败"));
    }

    /** 切换作品返回结构：新 token + userId + workId（前端 writeAuth 换 token） */
    public record SwitchResult(String token, long userId, long workId) {
    }

    /**
     * 切换作品：校验目标作品属于当前 userId → 用新 workId 重签 JWT。
     * ① findById 无 userId 条件，必须比对 w.userId() 防越权；
     * ② 目标 workId 是切换动作的必然输入，属唯一允许前端传 workId 的场景。
     */
    public SwitchResult switchWork(long userId, long targetWorkId) {
        WorkRepository.Work work = workRepository.findById(targetWorkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        if (work.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权切换他人作品");
        }
        String token = jwtService.issue(userId, targetWorkId);
        return new SwitchResult(token, userId, targetWorkId);
    }

    /** 更新作品信息：校验归属 → 更新标题/类型/简介/作者，返回更新后的完整作品记录 */
    public WorkRepository.Work update(long userId, long workId, String title, String genre, String intro, String author) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "作品标题不能为空");
        }
        WorkRepository.Work existing = workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        if (existing.userId() == null || existing.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权编辑他人作品");
        }
        int rows = workRepository.update(workId, userId, title, genre, intro, author);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        return workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "作品更新失败"));
    }

    /**
     * 删除作品：级联删除该作品全部章节/设定/伏笔/时间线/待确认冲突/审查报告及向量索引。
     * 事务性：所有向量 metadata 都带 workId，一步删干净 vector_store；业务表与向量同库，
     * 整个级联放进一个 @Transactional 方法原子完成。
     */
    @Transactional
    public void delete(long userId, long workId) {
        WorkRepository.Work work = workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        if (work.userId() == null || work.userId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除他人作品");
        }

        // ① 删向量索引（type 不限，按 workId 全删）
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'workId' = ?", String.valueOf(workId));

        // ② 删事实源表
        chapterRepository.deleteByWorkId(workId);
        worldSettingRepository.deleteByWorkId(workId);
        foreshadowRepository.deleteByWorkId(workId);
        timelineEventRepository.deleteByWorkId(workId);
        pendingConflictRepository.deleteByWorkId(workId);
        jdbcTemplate.update("DELETE FROM consistency_report WHERE work_id = ?", workId);
        workRepository.deleteById(workId, userId);

        log.info("删除作品并级联清理：userId={} workId={}", userId, workId);
    }
}
