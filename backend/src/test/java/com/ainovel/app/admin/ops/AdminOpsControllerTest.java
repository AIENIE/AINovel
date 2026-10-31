package com.ainovel.app.admin.ops;

import com.ainovel.app.metrics.ApiRequestMetrics;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.model.GlobalSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOpsControllerTest {
    @Test
    void retainsCentralLogsAndRejectsRetiredQueries() throws Exception {
        var dependencies = mock(DependencyHealthService.class);
        when(dependencies.checkAll()).thenReturn(List.of());
        var settings = mock(SettingsService.class);
        when(settings.getGlobalSettings()).thenReturn(new GlobalSettings());
        var controller = new AdminOpsController(new ApiRequestMetrics(), dependencies, settings, "local");
        var summary = controller.summary();
        assertEquals("https://control.testhut.top/config-center/logs?component=ai-novel&environment=develop", summary.get("logsUrl"));
        assertFalse(summary.containsKey("es"));
        var http = MockMvcBuilders.standaloneSetup(controller).build();
        for (String operation : List.of("events", "audit")) {
            http.perform(get("/v1/admin/ops/" + operation)).andExpect(status().isNotFound());
        }
    }
}
