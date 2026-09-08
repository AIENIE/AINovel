package com.ainovel.app.narrative;
import com.ainovel.app.aioperation.AiOperationService;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static com.ainovel.app.narrative.NarrativeDtos.*;

@Service
public class NarrativeApprovalCoordinator {
    private final NarrativeService narrative;
    private final AiOperationService operations;
    public NarrativeApprovalCoordinator(NarrativeService narrative, AiOperationService operations) {
        this.narrative = narrative; this.operations = operations;
    }
    @Transactional
    public Approved approve(User user, UUID manuscriptId, UUID branchId, ApprovalRequest request, String key) {
        Approved result = narrative.approve(user, manuscriptId, branchId, request, key);
        if (result.operationId() != null) return result;
        var operation = operations.submit(user, NarrativeExtractionHandler.TYPE, "narrative_extraction", result.extractionId(),
                Map.of("extractionId", result.extractionId()), 3, "固定正文版本",
                "narrative:" + result.extractionId());
        narrative.attachOperation(result.extractionId(), operation.operationId());
        return new Approved(result.approvalId(), result.extractionId(), operation.operationId());
    }
}
