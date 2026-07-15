package com.ainovel.app.workflow;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.common.BusinessException;
import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
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
        GuidedCreationStep step = job.getStep();
        Map<String, Object> confirmedContext = confirmedContext(run);
        String prompt = promptFactory.build(run, step, confirmedContext, hint);
        AiUsageContext usageContext = new AiUsageContext(
                "G1_WORKFLOW", job.getId().toString(), step.name().toLowerCase());
        AiChatResponse response = chat(run, prompt, usageContext);
        long charged = chargedCredits(response);
        Map<String, Object> normalized;
        try {
            normalized = normalize(run, step, parseJsonObject(response.content()));
        } catch (BusinessException firstFailure) {
            AiChatResponse repaired = chat(run, repairPrompt(prompt, response.content()),
                    usageContext.forOperation("json_repair"));
            charged += chargedCredits(repaired);
            response = repaired;
            normalized = normalize(run, step, parseJsonObject(repaired.content()));
        }
        normalized.put("promptVersion", promptFactory.version(step));
        normalized.put("generatedAt", Instant.now().toString());
        long remaining = Math.max(0L, Math.round(response.remainingCredits()));
        normalized.put("chargedCredits", charged);
        normalized.put("remainingCredits", remaining);
        return new GenerationResult(normalized, charged, remaining);
    }

    private AiChatResponse chat(CreationWorkflowRun run, String prompt, AiUsageContext usageContext) {
        return aiService.chat(
                run.getUser(),
                new AiChatRequest(List.of(new AiChatRequest.Message("user", prompt)), null, null),
                usageContext
        );
    }

    private long chargedCredits(AiChatResponse response) {
        return Math.max(0L, Math.round(response.usage() == null ? 0d : response.usage().cost()));
    }

    private String repairPrompt(String originalPrompt, String invalidOutput) {
        String source = invalidOutput == null ? "" : invalidOutput;
        return """
                请把下方输出修复为符合原任务的严格 JSON。只输出一个 JSON 对象，不要解释，不要 Markdown。
                必须保留恰好 3 个完整候选及 recommendedIndex；不得用省略号删减内容。

                原任务：
                %s

                待修复输出：
                %s
                """.formatted(originalPrompt, source);
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
            return objectMapper.readValue(candidate.substring(first, last + 1), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BusinessException("AI 返回的候选格式无效，请重试");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize(CreationWorkflowRun run,
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
            validateCandidate(run, step, candidate);
            candidate.put("candidateId", UUID.randomUUID().toString());
            candidates.add(candidate);
        }
        int recommendedIndex = parsed.get("recommendedIndex") instanceof Number number ? number.intValue() : 0;
        if (recommendedIndex < 0 || recommendedIndex > 2) recommendedIndex = 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", candidates);
        result.put("recommendedCandidateId", candidates.get(recommendedIndex).get("candidateId"));
        return result;
    }

    private void validateCandidate(CreationWorkflowRun run,
                                   GuidedCreationStep step,
                                   Map<String, Object> candidate) {
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
                Object chapters = candidate.get("chapters");
                if (!(chapters instanceof List<?> list) || list.size() != run.getTargetChapterCount()) {
                    throw new BusinessException("大纲候选必须包含 " + run.getTargetChapterCount() + " 章");
                }
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> chapter)
                            || !(chapter.get("scenes") instanceof List<?> scenes)
                            || scenes.size() < 2 || scenes.size() > 4) {
                        throw new BusinessException("每章必须包含 2-4 个场景");
                    }
                }
            }
            case COMPLETED -> throw new BusinessException("完成状态不能生成候选");
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

    public record GenerationResult(Map<String, Object> stepData, long chargedCredits, long remainingCredits) {}
}
