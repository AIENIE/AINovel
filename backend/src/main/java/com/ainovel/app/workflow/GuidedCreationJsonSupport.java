package com.ainovel.app.workflow;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GuidedCreationJsonSupport {
    private final JsonColumnCodec codec;

    public GuidedCreationJsonSupport(JsonColumnCodec codec) {
        this.codec = codec;
    }

    public Map<String, Object> readSteps(CreationWorkflowRun run) {
        return new LinkedHashMap<>(codec.read(run.getStepsJson(), new TypeReference<>() {}, Map.of()));
    }

    public void writeSteps(CreationWorkflowRun run, Map<String, Object> steps) {
        run.setStepsJson(codec.write(steps, "{}"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> stepData(Map<String, Object> steps, GuidedCreationStep step) {
        Object value = steps.get(step.name());
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> candidates(Map<String, Object> stepData) {
        Object value = stepData.get("candidates");
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    public Map<String, Object> requireCandidate(Map<String, Object> stepData, String candidateId) {
        return candidates(stepData).stream()
                .filter(candidate -> candidateId.equals(String.valueOf(candidate.get("candidateId"))))
                .findFirst()
                .orElseThrow(() -> new BusinessException("候选不存在或已经失效"));
    }

    public void recordSelection(Map<String, Object> stepData,
                                String candidateId,
                                Map<String, Object> selected,
                                Object entityIds) {
        stepData.put("selectedCandidateId", candidateId);
        stepData.put("selected", selected);
        stepData.put("confirmedAt", Instant.now().toString());
        if (entityIds != null) stepData.put("entityIds", entityIds);
    }
}
