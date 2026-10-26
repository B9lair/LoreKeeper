package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.ChapterRepository;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.TimelineEventRepository;
import com.xiaoyu.aiaigent.repository.WorldSettingRepository;
import com.xiaoyu.aiaigent.repository.WorkRepository;
import com.xiaoyu.aiaigent.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class WorkServiceTest {

    private final WorkRepository workRepository = mock(WorkRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final WorkService service = new WorkService(workRepository,
            mock(ChapterRepository.class), mock(WorldSettingRepository.class),
            mock(ForeshadowRepository.class), mock(TimelineEventRepository.class),
            mock(PendingConflictRepository.class), jwtService, mock(JdbcTemplate.class));

    @Test
    void switchWork_ownWork_reissuesToken() {
        when(workRepository.findById(42L))
                .thenReturn(Optional.of(new WorkRepository.Work(42L, 1L, "作品", null, null, null)));
        when(jwtService.issue(1L, 42L)).thenReturn("new-token");

        WorkService.SwitchResult r = service.switchWork(1L, 42L);

        assertEquals("new-token", r.token());
        assertEquals(42L, r.workId());
        verify(jwtService).issue(1L, 42L);
    }

    @Test
    void switchWork_otherUsersWork_throws403() {
        when(workRepository.findById(42L))
                .thenReturn(Optional.of(new WorkRepository.Work(42L, 2L, "他人作品", null, null, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.switchWork(1L, 42L));
        assertEquals(403, ex.getStatusCode().value());
        verify(jwtService, never()).issue(anyLong(), anyLong());
    }

    @Test
    void switchWork_notFound_throws404() {
        when(workRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.switchWork(1L, 99L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void update_ownWork_updates() {
        when(workRepository.findById(42L))
                .thenReturn(Optional.of(new WorkRepository.Work(42L, 1L, "新名", "玄幻", "简介", "作者")));
        when(workRepository.update(42L, 1L, "新名", "玄幻", "简介", "作者")).thenReturn(1);

        WorkRepository.Work r = service.update(1L, 42L, "新名", "玄幻", "简介", "作者");

        assertEquals("新名", r.title());
        assertEquals("作者", r.author());
        verify(workRepository).update(42L, 1L, "新名", "玄幻", "简介", "作者");
    }

    @Test
    void update_otherUsersWork_throws403() {
        when(workRepository.findById(42L))
                .thenReturn(Optional.of(new WorkRepository.Work(42L, 2L, "他人", null, null, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.update(1L, 42L, "新名", null, null, null));
        assertEquals(403, ex.getStatusCode().value());
    }
}
