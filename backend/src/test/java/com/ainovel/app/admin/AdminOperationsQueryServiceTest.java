package com.ainovel.app.admin;

import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.quality.model.PlotQualityRun;
import com.ainovel.app.quality.model.SlopQualityRun;
import com.ainovel.app.quality.repo.PlotQualityRunRepository;
import com.ainovel.app.quality.repo.SlopQualityRunRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOperationsQueryServiceTest {

    @Test
    void assetSummaryShouldAggregateRepositoryCounts() {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        StoryRepository storyRepository = mock(StoryRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        SlopQualityRunRepository slopQualityRunRepository = mock(SlopQualityRunRepository.class);
        PlotQualityRunRepository plotQualityRunRepository = mock(PlotQualityRunRepository.class);
        when(storyRepository.count()).thenReturn(11L);
        when(worldRepository.count()).thenReturn(7L);
        when(manuscriptRepository.count()).thenReturn(5L);
        when(materialRepository.count()).thenReturn(13L);
        when(materialRepository.countByStatusIgnoreCase("pending")).thenReturn(3L);
        when(slopQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70)).thenReturn(2L);
        when(plotQualityRunRepository.countByOverallRiskScoreGreaterThanEqual(70)).thenReturn(4L);

        AdminOperationsQueryService service = new AdminOperationsQueryService(
                materialRepository,
                storyRepository,
                worldRepository,
                manuscriptRepository,
                slopQualityRunRepository,
                plotQualityRunRepository, mock(AdminOperationsReadRepository.class)
        );

        Map<String, Object> result = service.assetSummary();

        assertEquals(11L, result.get("stories"));
        assertEquals(7L, result.get("worlds"));
        assertEquals(5L, result.get("manuscripts"));
        assertEquals(13L, result.get("materials"));
        assertEquals(3L, result.get("pendingMaterials"));
        assertEquals(6L, result.get("highRiskQualityRuns"));
    }

    @Test
    void validatesPageAndDelegatesOnlyBoundedReadModelQueries() {
        AdminOperationsReadRepository read = mock(AdminOperationsReadRepository.class);
        AdminOperationsQueryService service = new AdminOperationsQueryService(mock(MaterialRepository.class), mock(StoryRepository.class),
                mock(WorldRepository.class), mock(ManuscriptRepository.class), mock(SlopQualityRunRepository.class), mock(PlotQualityRunRepository.class), read);
        when(read.assets("stories", 20, 20, "needle")).thenReturn(new AdminOperationsReadRepository.Result<>(List.of(), 45));
        var page = service.assets("stories", 1, 20, "needle");
        assertEquals(3, page.totalPages());
        assertEquals(45, page.totalElements());
        org.mockito.Mockito.verify(read).assets("stories", 20, 20, "needle");
        for (int size : new int[]{0, -1, 101, Integer.MAX_VALUE}) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.assets("stories", 0, size, ""));
        }
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.assets("stories", -1, 20, ""));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.assets("stories", Integer.MAX_VALUE, 100, ""));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.qualityRuns(0, 20, "", "invalid"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.assets("stories", 0, 20, "x".repeat(201)));
        org.mockito.Mockito.verifyNoMoreInteractions(read);
    }

    @Test
    void readOnlyQueryMethodsShouldDeclareReadOnlyTransactions() throws Exception {
        for (Method method : AdminOperationsQueryService.class.getDeclaredMethods()) {
            if (!List.of("assetSummary", "assets", "qualityRuns").contains(method.getName())) continue;
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertTrue(transactional != null && transactional.readOnly());
        }
    }
}
