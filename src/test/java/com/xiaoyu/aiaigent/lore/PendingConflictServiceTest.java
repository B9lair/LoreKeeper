package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.repository.PendingConflictRepository;
import com.xiaoyu.aiaigent.repository.PendingConflictRepository.PendingConflict;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PendingConflictServiceTest {

    private final PendingConflictRepository pendingConflictRepository = mock(PendingConflictRepository.class);
    private final SettingService settingService = mock(SettingService.class);
    private final PendingConflictService service =
            new PendingConflictService(pendingConflictRepository, settingService);

    private PendingConflict pc(long id, String status) {
        return new PendingConflict(id, 1L, "主角", "惯用手", "左撇子", "右手", "第50章主角右手持剑", 50, status);
    }

    @Test
    void accept_updatesSettingAndStatus() {
        when(pendingConflictRepository.findById(7L, 1L))
                .thenReturn(Optional.of(pc(7L, "pending")))    // findPending 用
                .thenReturn(Optional.of(pc(7L, "accepted")));  // accept 末尾返回用
        when(pendingConflictRepository.updateStatus(7L, 1L, "accepted")).thenReturn(1);

        PendingConflict r = service.accept(1L, 7L);

        assertEquals("accepted", r.status());
        verify(settingService).applyValue(1L, "主角", "惯用手", "右手", "第50章主角右手持剑", 50);
        verify(pendingConflictRepository).updateStatus(7L, 1L, "accepted");
    }

    @Test
    void accept_alreadyProcessed_throws409() {
        when(pendingConflictRepository.findById(7L, 1L)).thenReturn(Optional.of(pc(7L, "accepted")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.accept(1L, 7L));
        assertEquals(409, ex.getStatusCode().value());
        verify(settingService, never()).applyValue(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void reject_onlyUpdatesStatus() {
        when(pendingConflictRepository.findById(7L, 1L))
                .thenReturn(Optional.of(pc(7L, "pending")))    // findPending 用
                .thenReturn(Optional.of(pc(7L, "rejected")));  // reject 末尾返回用
        when(pendingConflictRepository.updateStatus(7L, 1L, "rejected")).thenReturn(1);

        PendingConflict r = service.reject(1L, 7L);

        assertEquals("rejected", r.status());
        verify(settingService, never()).applyValue(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void reject_notFound_throws404() {
        when(pendingConflictRepository.findById(99L, 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reject(1L, 99L));
        assertEquals(404, ex.getStatusCode().value());
    }
}
