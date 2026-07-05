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
                plotQualityRunRepository
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
    void qualityRunsShouldMergeKindsByNewestFirst() {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        StoryRepository storyRepository = mock(StoryRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        ManuscriptRepository manuscriptRepository = mock(ManuscriptRepository.class);
        SlopQualityRunRepository slopQualityRunRepository = mock(SlopQualityRunRepository.class);
        PlotQualityRunRepository plotQualityRunRepository = mock(PlotQualityRunRepository.class);
        SlopQualityRun olderSlopRun = mock(SlopQualityRun.class);
        PlotQualityRun newerPlotRun = mock(PlotQualityRun.class);
        when(olderSlopRun.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(olderSlopRun.getCreatedAt()).thenReturn(Instant.parse("2026-07-01T10:15:30Z"));
        when(newerPlotRun.getId()).thenReturn(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(newerPlotRun.getCreatedAt()).thenReturn(Instant.parse("2026-07-02T10:15:30Z"));
        when(slopQualityRunRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(olderSlopRun));
        when(plotQualityRunRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(newerPlotRun));

        AdminOperationsQueryService service = new AdminOperationsQueryService(
                materialRepository,
                storyRepository,
                worldRepository,
                manuscriptRepository,
                slopQualityRunRepository,
                plotQualityRunRepository
        );

        List<Map<String, Object>> result = service.qualityRuns();

        assertEquals(2, result.size());
        assertEquals("plot", result.get(0).get("kind"));
        assertEquals(newerPlotRun.getId(), result.get(0).get("id"));
        assertEquals("slop", result.get(1).get("kind"));
        assertEquals(olderSlopRun.getId(), result.get(1).get("id"));
    }

    @Test
    void readOnlyQueryMethodsShouldDeclareReadOnlyTransactions() throws Exception {
        for (String methodName : List.of("assetSummary", "stories", "worlds", "manuscripts", "qualityRuns")) {
            Method method = AdminOperationsQueryService.class.getMethod(methodName);
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertTrue(transactional != null && transactional.readOnly(),
                    methodName + " should declare a read-only transactional boundary");
        }
    }
}
