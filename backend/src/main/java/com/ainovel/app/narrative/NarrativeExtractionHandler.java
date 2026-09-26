package com.ainovel.app.narrative;
import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.aioperation.*;
import com.ainovel.app.economy.EconomyService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class NarrativeExtractionHandler implements AiOperationHandler {
    @org.springframework.beans.factory.annotation.Autowired
    private AiModelPolicy modelPolicy = new AiModelPolicy();
    public static final String TYPE = "NARRATIVE_STATE_EXTRACT";
    private final NarrativeService narrative;
    private final AiService ai;
    private final EconomyService economy;
    public NarrativeExtractionHandler(NarrativeService narrative, AiService ai, EconomyService economy) {
        this.narrative = narrative; this.ai = ai; this.economy = economy;
    }
    @Override public String type() { return TYPE; }
    @Override public void recoverExpiredLease(AiOperationExecution execution) throws Exception {
        UUID extractionId = UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("extractionId").asText());
        // Lease expiry is not evidence that the gateway did not execute inference.
        economy.markAiResultUncertain(execution.user(),
                new AiUsageContext("NARRATIVE_EXTRACTION", extractionId.toString(), "p11-v1").idempotencyKey());
    }
    @Override public Object execute(AiOperationExecution execution) throws Exception {
        UUID extractionId = UUID.fromString(execution.objectMapper().readTree(execution.payloadJson()).path("extractionId").asText());
        if (AiOperationExecutionContext.apply(() -> narrative.hasResult(execution.user(), extractionId, execution.operationId())))
            return AiOperationExecutionContext.complete(() -> Map.of("extractionId", extractionId));
        var input = AiOperationExecutionContext.apply(() -> narrative.input(execution.user(), extractionId, execution.operationId()));
        String prompt = new ClassPathResource("narrative/" + ("narrative-state-p11-v4".equals(input.promptVersion()) ? "narrative-state-p11-v4.txt"
                : "narrative-state-p11-v3".equals(input.promptVersion()) ? "narrative-state-p11-v3.txt"
                : input.knowledgeEnabled() ? "narrative-state-p11-v2.txt" : "narrative-state-p11-v1.txt")).getContentAsString(StandardCharsets.UTF_8);
        execution.progress().step("提取带证据的变化候选", 1, 3);
        var result = ai.chat(execution.user(), new AiChatRequest(List.of(
                new AiChatRequest.Message("system", prompt),
                new AiChatRequest.Message("user", execution.objectMapper().writeValueAsString(input))), null, null),
                new AiUsageContext("NARRATIVE_EXTRACTION", extractionId.toString(), "p11-v1"));
        return AiOperationExecutionContext.complete(() -> {
            narrative.storeResult(execution.user(), extractionId, execution.operationId(), result.content(), result.usage(), modelPolicy.modelKey());
            return Map.of("extractionId", extractionId);
        });
    }
}
