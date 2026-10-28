package com.ainovel.app.modelconfig;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.v2.model.V2ModelRegistry;
import com.ainovel.app.v2.repo.V2ModelRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiNativeCatalogTest {
    @Test void absentCombinedCapabilityStaysUnknownEvenWhenSeparateFeaturesAreAvailable() {
        var service=new AiModelConfigurationService(mock(V2ModelRegistryRepository.class),mock(JdbcTemplate.class),new ObjectMapper(),mock(TransactionTemplate.class),new AiModelPolicy(),mock(AiGatewayGrpcClient.class));
        var model=new V2ModelRegistry();model.setModelKey("model");model.setDisplayName("Model");model.setProvider("provider");
        assertFalse(service.catalogModel(model).chatCapabilities().budgetToolsDeclared());
        model.setCapabilitiesJson("{\"legacy\":true,\"gatewayChat\":{\"budgetDeclared\":true,\"budgetAvailable\":true,\"toolsDeclared\":true,\"supportsTools\":true}}");
        var separate=service.catalogModel(model).chatCapabilities();
        assertTrue(separate.budgetAvailable());assertTrue(separate.supportsTools());assertFalse(separate.budgetToolsDeclared());assertFalse(separate.budgetToolsAvailable());
        model.setCapabilitiesJson("{\"gatewayChat\":{\"budgetToolsDeclared\":true,\"budgetToolsAvailable\":true,\"maxPromptTokens\":10000,\"maxOutputTokens\":4096}}");
        assertTrue(service.catalogModel(model).chatCapabilities().budgetToolsAvailable());
        assertEquals(10000,service.catalogModel(model).chatCapabilities().maxPromptTokens());
    }
}
