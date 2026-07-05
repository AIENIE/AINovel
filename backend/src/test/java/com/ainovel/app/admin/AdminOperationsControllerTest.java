package com.ainovel.app.admin;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.material.MaterialService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOperationsControllerTest {

    @Test
    void readOnlyQueriesShouldDelegateToQueryServiceWithoutControllerTransactions() throws Exception {
        MaterialService materialService = mock(MaterialService.class);
        AdminOperationsQueryService queryService = mock(AdminOperationsQueryService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminOperationsController controller = new AdminOperationsController(materialService, queryService, recordFileSink);

        Map<String, Object> summary = Map.of("stories", 3L);
        List<Map<String, Object>> stories = List.of(Map.of("id", "story-1"));
        List<Map<String, Object>> worlds = List.of(Map.of("id", "world-1"));
        List<Map<String, Object>> manuscripts = List.of(Map.of("id", "manuscript-1"));
        List<Map<String, Object>> qualityRuns = List.of(Map.of("id", "run-1"));
        when(queryService.assetSummary()).thenReturn(summary);
        when(queryService.stories()).thenReturn(stories);
        when(queryService.worlds()).thenReturn(worlds);
        when(queryService.manuscripts()).thenReturn(manuscripts);
        when(queryService.qualityRuns()).thenReturn(qualityRuns);

        assertSame(summary, controller.assetSummary());
        assertSame(stories, controller.stories());
        assertSame(worlds, controller.worlds());
        assertSame(manuscripts, controller.manuscripts());
        assertSame(qualityRuns, controller.qualityRuns());

        for (String methodName : List.of("assetSummary", "stories", "worlds", "manuscripts", "qualityRuns")) {
            Method method = AdminOperationsController.class.getMethod(methodName);
            assertNull(method.getAnnotation(Transactional.class), methodName + " should not declare transactional boundary on controller");
        }
    }
}
