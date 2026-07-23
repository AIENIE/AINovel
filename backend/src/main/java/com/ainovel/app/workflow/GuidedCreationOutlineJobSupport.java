package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GuidedCreationOutlineJobSupport {
    private final GuidedCreationJsonSupport jsonSupport;

    public GuidedCreationOutlineJobSupport(GuidedCreationJsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public PreparedOperation prepare(CreationWorkflowRun run,
                                     String directionId,
                                     GuidedCreationOperation operation,
                                     String instruction,
                                     Map<String, Object> editedPayload,
                                     Long expectedVersion) {
        if (run.getCurrentStep() != GuidedCreationStep.OUTLINE
                || run.getStatus() == CreationWorkflowStatus.COMPLETED) {
            throw new BusinessException("当前不在大纲方向步骤");
        }
        if (expectedVersion != null && expectedVersion.longValue() != run.getVersion()) {
            throw new BusinessException("向导草稿已更新，请刷新后重试");
        }
        if (operation != GuidedCreationOperation.OUTLINE_DEVELOP
                && operation != GuidedCreationOperation.OUTLINE_REWRITE
                && operation != GuidedCreationOperation.OUTLINE_EXPAND) {
            throw new BusinessException("不支持的大纲操作");
        }
        String normalizedInstruction = instruction == null ? "" : instruction.trim();
        if (operation == GuidedCreationOperation.OUTLINE_REWRITE && normalizedInstruction.isBlank()) {
            throw new BusinessException("重写方向时必须填写反馈");
        }

        Map<String, Object> stepData = jsonSupport.stepData(
                jsonSupport.readSteps(run), GuidedCreationStep.OUTLINE);
        if (!"DIRECTION_SELECTION".equals(String.valueOf(stepData.get("outlinePhase")))) {
            throw new BusinessException("当前大纲阶段不允许执行方向推演");
        }
        Map<String, Object> direction = jsonSupport.requireCandidate(stepData, directionId);
        long revision = numeric(direction.get("developmentRevision"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation.name());
        payload.put("directionId", directionId);
        payload.put("instruction", normalizedInstruction);
        payload.put("editedPayload", editedPayload == null ? Map.of() : editedPayload);
        payload.put("revision", revision);
        String key = "g1:" + run.getId() + ":outline:" + operation.name().toLowerCase()
                + ":" + directionId + ":" + revision;
        return new PreparedOperation(key, payload);
    }

    public Map<String, Object> mergeResult(Map<String, Object> stepData,
                                           Map<String, Object> jobPayload,
                                           GuidedCreationOperation operation,
                                           Map<String, Object> generated) {
        String directionId = String.valueOf(jobPayload.get("directionId"));
        if (operation == GuidedCreationOperation.OUTLINE_EXPAND) {
            Map<String, Object> outline = new LinkedHashMap<>(generated);
            outline.put("candidateId", directionId);
            stepData.put("outlinePhase", "OUTLINE_PREVIEW");
            stepData.put("selectedDirectionId", directionId);
            stepData.put("expandedOutline", outline);
        } else {
            List<Map<String, Object>> candidates = jsonSupport.candidates(stepData);
            Map<String, Object> direction = candidates.stream()
                    .filter(item -> directionId.equals(String.valueOf(item.get("candidateId"))))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("大纲方向不存在或已经失效"));
            direction.put("development", generated);
            direction.put("developmentRevision", numeric(direction.get("developmentRevision")) + 1L);
            stepData.put("candidates", candidates);
        }
        stepData.put("lastOperation", operation.name());
        stepData.put("updatedAt", Instant.now().toString());
        return stepData;
    }

    private long numeric(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record PreparedOperation(String idempotencyKey, Map<String, Object> payload) {}
}
