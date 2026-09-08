package com.ainovel.app.narrative;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.aioperation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class NarrativeExtractionHandler implements AiOperationHandler {
    public static final String TYPE = "NARRATIVE_STATE_EXTRACT";
    private final NarrativeService narrative;
    private final AiService ai;
    public NarrativeExtractionHandler(NarrativeService narrative, AiService ai) { this.narrative = narrative; this.ai = ai; }
    @Override public String type() { return TYPE; }
    @Override public Object execute(AiOperationExecution execution) throws Exception {
        UUID extractionId = UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("extractionId").asText());
        if (narrative.hasResult(execution.user(), extractionId, execution.operationId())) return Map.of("extractionId", extractionId);
        var input = narrative.input(execution.user(), extractionId, execution.operationId());
        String prompt = new ClassPathResource("narrative/narrative-state-p11-v1.txt").getContentAsString(StandardCharsets.UTF_8);
        execution.progress().step("提取带证据的变化候选", 1, 3);
        var result = ai.chat(execution.user(), new AiChatRequest(List.of(
                new AiChatRequest.Message("system", prompt),
                new AiChatRequest.Message("user", execution.objectMapper().writeValueAsString(input))), null, null),
                new AiUsageContext("NARRATIVE_EXTRACTION", extractionId.toString(), "p11-v1"));
        narrative.storeResult(execution.user(), extractionId, execution.operationId(), result.content(), result.usage(),
                AiModelPolicy.REQUIRED_TEXT_MODEL_KEY);
        execution.progress().step("校验证据并保留待审候选", 2, 3);
        return Map.of("extractionId", extractionId);
    }
}
