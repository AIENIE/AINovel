package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the generated public contract, rather than annotation presence alone. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeneratedOpenApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;

    @Test void applicationDoesNotHostAGrpcNettyServer() {
        assertTrue(context.getBeansOfType(io.grpc.Server.class).isEmpty(),
                "The shaded Netty SNI advisory is only excepted for this client-only application");
    }

    @Test void criticalWriteAndRecoveryOperationsExposeRequestAndErrorContracts() throws Exception {
        JsonNode document = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode paths = document.path("paths");
        assertCritical(paths, "/v2/manuscripts/{manuscriptId}/branches/{branchId}/merge", "post", true, "400", "409");
        assertCritical(paths, "/v2/manuscripts/{manuscriptId}/versions/{versionId}/rollback", "post", false, "400", "409");
        assertCritical(paths, "/v1/manuscripts/{id}/scenes/{sceneId}/content", "put", true, "400", "409");
        assertCritical(paths, "/v1/ai-operations/{id}/cancel", "post", false, "200", "404");
        assertCritical(paths, "/v2/manuscripts/{manuscriptId}/export", "post", true, "200", "400");
        assertCritical(paths, "/v2/manuscripts/{manuscriptId}/export/jobs/{jobId}/download", "get", false, "200", "429");
        String merge = paths.path("/v2/manuscripts/{manuscriptId}/branches/{branchId}/merge").path("post").toString();
        assertTrue(merge.contains("REPLACE_ALL") && merge.contains("SCENE_SELECT"));
        assertFalse(document.path("components").path("schemas").isEmpty(), "DTO schemas must be generated");
    }

    private static void assertCritical(JsonNode paths, String path, String method, boolean request, String... responses) {
        JsonNode operation = paths.path(path).path(method);
        assertFalse(operation.isMissingNode(), path + " " + method + " missing from generated OpenAPI");
        assertTrue(operation.path("summary").asText().length() > 5, path + " summary missing");
        assertNotEquals("v2 API endpoint", operation.path("summary").asText());
        if (request) assertFalse(operation.path("requestBody").path("content").path("application/json")
                .path("schema").isMissingNode(), path + " request schema missing");
        for (String status : responses) assertFalse(operation.path("responses").path(status).isMissingNode(),
                path + " response " + status + " missing");
    }
}
