package com.ainovel.app.admin.ops;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class OpsRecordSearchServiceTest {
    @Test
    void retiredQueriesKeepTheirResponseAndUseTheExplicitDeploymentEnvironment() {
        OpsRecordSearchService service = new OpsRecordSearchService();
        for (String environment : new String[]{"local", "test", "production"}) {
            ReflectionTestUtils.setField(service, "environment", environment);
            String mapped = switch (environment) {
                case "local" -> "develop";
                case "test" -> "staging";
                default -> "production";
            };
            assertTrue(service.consoleUrl().endsWith("&environment=" + mapped));
            assertTrue(service.consoleUrl().contains("component=ai-novel"));
            assertEquals(true, service.status().get("retired"));
            var page = service.search("audit", null, null, null, null, null, null, null, 2, 20);
            assertFalse(page.available());
            assertTrue(page.items().isEmpty());
            assertEquals(2, page.page());
            assertEquals(20, page.size());
        }
    }
}
