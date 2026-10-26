package com.xiaoyu.aiaigent.lore;

import com.xiaoyu.aiaigent.rag.ChunkingService;
import com.xiaoyu.aiaigent.repository.ForeshadowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ForeshadowServiceTest {

    private final ForeshadowRepository foreshadowRepository = mock(ForeshadowRepository.class);
    private final ChunkingService chunkingService = mock(ChunkingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ForeshadowService service =
            new ForeshadowService(foreshadowRepository, chunkingService, vectorStore, mock(JdbcTemplate.class));

    @Test
    void create_persistsAndIndexes() {
        when(foreshadowRepository.save(eq(1L), eq("伏笔"), eq(3))).thenReturn(7L);
        when(foreshadowRepository.findById(7L))
                .thenReturn(Optional.of(new ForeshadowRepository.Foreshadow(7L, 1L, "伏笔", 3, null, "open")));
        when(chunkingService.settingToDocument(anyLong(), anyLong(), anyInt(), anyString(), anyString()))
                .thenReturn(new Document("伏笔", Map.of()));

        ForeshadowRepository.Foreshadow f = service.create(1L, "伏笔", 3);

        assertEquals(7L, f.id());
        assertEquals("open", f.status());
        verify(vectorStore).add(anyList());   // 新建伏笔也要向量化
    }

    @Test
    void resolve_markResolved() {
        when(foreshadowRepository.markResolved(7L, 1L, 10)).thenReturn(1);
        when(foreshadowRepository.findById(7L))
                .thenReturn(Optional.of(new ForeshadowRepository.Foreshadow(7L, 1L, "伏笔", 3, 10, "closed")));

        ForeshadowRepository.Foreshadow f = service.resolve(1L, 7L, 10);

        assertEquals("closed", f.status());
        assertEquals(10, f.resolvedChapter());
    }

    @Test
    void resolve_wrongWork_throws404() {
        when(foreshadowRepository.markResolved(7L, 1L, 10)).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolve(1L, 7L, 10));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void create_blankContent_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(1L, "  ", 3));
        assertEquals(400, ex.getStatusCode().value());
    }
}
