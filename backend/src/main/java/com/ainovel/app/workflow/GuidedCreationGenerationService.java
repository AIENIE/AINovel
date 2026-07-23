package com.ainovel.app.workflow;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GuidedCreationGenerationService {
    private final AiService aiService;
    private final GuidedCreationPromptFactory promptFactory;
    private final GuidedCreationJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public GuidedCreationGenerationService(AiService aiService,
                                           GuidedCreationPromptFactory promptFactory,
                                           GuidedCreationJsonSupport jsonSupport,
                                           ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.promptFactory = promptFactory;
        this.jsonSupport = jsonSupport;
        this.objectMapper = objectMapper;
    }

    public GenerationResult generate(CreationWorkflowRun run, AsyncJob job, String hint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", GuidedCreationOperation.STEP_CANDIDATES.name());
        payload.put("hint", hint == null ? "" : hint);
        return generate(run, job, payload);
    }

    public GenerationResult generate(CreationWorkflowRun run,
                                     AsyncJob job,
                                     Map<String, Object> payload) {
        GuidedCreationStep step = job.getStep();
        GuidedCreationOperation operation = operation(payload);
        Map<String, Object> confirmedContext = confirmedContext(run);
        String prompt;
        if (operation == GuidedCreationOperation.STEP_CANDIDATES) {
            prompt = promptFactory.build(run, step, confirmedContext, string(payload.get("hint")));
        } else {
            Map<String, Object> stepData = jsonSupport.stepData(
                    jsonSupport.readSteps(run), GuidedCreationStep.OUTLINE);
            Map<String, Object> direction = jsonSupport.requireCandidate(
                    stepData, string(payload.get("directionId")));
            prompt = promptFactory.buildOutlineOperation(
                    run, operation, confirmedContext, direction, payload);
        }

        AiUsageContext usageContext = new AiUsageContext(
                "G1_WORKFLOW", job.getId().toString(), operationName(step, operation));
        AiChatResponse response = chat(run, prompt, usageContext);
        long charged = chargedCredits(response);
        Map<String, Object> normalized;
        try {
            normalized = normalize(run, step, operation, parseJsonObject(response.content()));
        } catch (BusinessException firstFailure) {
            AiChatResponse repaired = chat(run, repairPrompt(prompt, response.content()),
                    usageContext.forOperation("json_repair"));
            charged += chargedCredits(repaired);
            response = repaired;
            normalized = normalize(run, step, operation, parseJsonObject(repaired.content()));
        }
        normalized.put("promptVersion", promptFactory.version(step, operation));
        normalized.put("generatedAt", Instant.now().toString());
        long remaining = Math.max(0L, Math.round(response.remainingCredits()));
        return new GenerationResult(normalized, charged, remaining);
    }

    private AiChatResponse chat(CreationWorkflowRun run, String prompt, AiUsageContext usageContext) {
        return aiService.chat(
                run.getUser(),
                new AiChatRequest(List.of(new AiChatRequest.Message("user", prompt)), null, null),
                usageContext);
    }

    private long chargedCredits(AiChatResponse response) {
        return Math.max(0L, Math.round(response.usage() == null ? 0d : response.usage().cost()));
    }

    private String repairPrompt(String originalPrompt, String invalidOutput) {
        return """
                请把下方输出修复为符合原任务的严格 JSON。只输出一个完整 JSON 对象，不要解释，不要 Markdown，也不要省略任何必需内容。

                原任务：
                %s

                待修复输出：
                %s
                """.formatted(originalPrompt, invalidOutput == null ? "" : invalidOutput);
    }

    private Map<String, Object> confirmedContext(CreationWorkflowRun run) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : jsonSupport.readSteps(run).entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map && map.get("selected") != null) {
                result.put(entry.getKey(), map.get("selected"));
            }
        }
        return result;
    }

    private Map<String, Object> parseJsonObject(String text) {
        if (text == null || text.isBlank()) throw new BusinessException("AI 返回空内容");
        String candidate = text.trim();
        int first = candidate.indexOf('{');
        int last = candidate.lastIndexOf('}');
        if (first < 0 || last <= first) throw new BusinessException("AI 未返回有效 JSON");
        try {
            return objectMapper.readValue(
                    candidate.substring(first, last + 1), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new BusinessException("AI 返回的候选格式无效，请重试");
        }
    }

    private Map<String, Object> normalize(CreationWorkflowRun run,
                                          GuidedCreationStep step,
                                          GuidedCreationOperation operation,
                                          Map<String, Object> parsed) {
        if (operation == GuidedCreationOperation.OUTLINE_EXPAND) {
            Map<String, Object> outline = new LinkedHashMap<>(parsed);
            validateExpandedOutline(run, outline);
            return outline;
        }
        if (operation == GuidedCreationOperation.OUTLINE_DEVELOP
                || operation == GuidedCreationOperation.OUTLINE_REWRITE) {
            Map<String, Object> development = new LinkedHashMap<>(parsed);
            requireText(development, "title", "narrativeArc", "escalation", "endingDirection");
            requireNonEmptyList(development, "keyTurns");
            return development;
        }
        return normalizeStepCandidates(run, step, parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeStepCandidates(CreationWorkflowRun run,
                                                        GuidedCreationStep step,
                                                        Map<String, Object> parsed) {
        Object rawCandidates = parsed.get("candidates");
        if (!(rawCandidates instanceof List<?> list) || list.size() != 3) {
            throw new BusinessException("AI 必须返回恰好 3 个候选");
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) throw new BusinessException("候选格式无效");
            Map<String, Object> candidate = new LinkedHashMap<>((Map<String, Object>) raw);
            validateCandidate(step, candidate);
            candidate.put("candidateId", UUID.randomUUID().toString());
            if (step == GuidedCreationStep.OUTLINE) {
                candidate.remove("chapters");
                candidate.remove("scenes");
                candidate.put("developmentRevision", 0L);
            }
            candidates.add(candidate);
        }
        int recommendedIndex = parsed.get("recommendedIndex") instanceof Number number ? number.intValue() : 0;
        if (recommendedIndex < 0 || recommendedIndex > 2) recommendedIndex = 0;
        Map<String, Object> result = new LinkedHashMap<>();
        if (step == GuidedCreationStep.OUTLINE) result.put("outlinePhase", "DIRECTION_SELECTION");
        result.put("candidates", candidates);
        result.put("recommendedCandidateId", candidates.get(recommendedIndex).get("candidateId"));
        return result;
    }

    private void validateCandidate(GuidedCreationStep step, Map<String, Object> candidate) {
        switch (step) {
            case PREMISE -> requireText(candidate, "title", "synopsis");
            case WORLD -> requireText(candidate, "name", "creativeIntent");
            case CHARACTERS -> {
                Object characters = candidate.get("characters");
                if (!(characters instanceof List<?> list) || list.size() < 3 || list.size() > 5) {
                    throw new BusinessException("每套角色阵容必须包含 3-5 名角色");
                }
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> character)
                            || character.get("name") == null
                            || String.valueOf(character.get("name")).isBlank()) {
                        throw new BusinessException("角色候选缺少姓名");
                    }
                }
            }
            case OUTLINE -> {
                requireText(candidate, "title", "summary", "coreConflict", "protagonistDrive", "stakes");
                if (candidate.containsKey("chapters") || candidate.containsKey("scenes")) {
                    throw new BusinessException("初始大纲方向不能包含章节或场景");
                }
            }
            case COMPLETED -> throw new BusinessException("完成状态不能生成候选");
        }
    }

    private void validateExpandedOutline(CreationWorkflowRun run, Map<String, Object> outline) {
        requireText(outline, "title");
        Object chapters = outline.get("chapters");
        if (!(chapters instanceof List<?> list) || list.size() != run.getTargetChapterCount()) {
            throw new BusinessException("大纲必须包含 " + run.getTargetChapterCount() + " 章");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> chapter)
                    || !(chapter.get("scenes") instanceof List<?> scenes)
                    || scenes.size() < 2 || scenes.size() > 4) {
                throw new BusinessException("每章必须包含 2-4 个场景");
            }
        }
    }

    private void requireText(Map<String, Object> candidate, String... keys) {
        for (String key : keys) {
            Object value = candidate.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BusinessException("候选缺少字段：" + key);
            }
        }
    }

    private void requireNonEmptyList(Map<String, Object> candidate, String key) {
        if (!(candidate.get(key) instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException("候选缺少字段：" + key);
        }
    }

    private GuidedCreationOperation operation(Map<String, Object> payload) {
        try {
            return GuidedCreationOperation.valueOf(string(payload.get("operation")));
        } catch (RuntimeException ignored) {
            return GuidedCreationOperation.STEP_CANDIDATES;
        }
    }

    private String operationName(GuidedCreationStep step, GuidedCreationOperation operation) {
        return operation == GuidedCreationOperation.STEP_CANDIDATES
                ? step.name().toLowerCase()
                : operation.name().toLowerCase();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record GenerationResult(Map<String, Object> stepData,
                                   long chargedCredits,
                                   long remainingCredits) {}
}
