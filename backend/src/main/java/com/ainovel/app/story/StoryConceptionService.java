package com.ainovel.app.story;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.story.dto.CharacterRequest;
import com.ainovel.app.story.dto.StoryCreateRequest;
import com.ainovel.app.story.dto.StoryUpdateRequest;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StoryConceptionService {
    @Autowired
    private AiService aiService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoryConceptionPromptFactory promptFactory;
    @Autowired
    private StoryConceptionProjectionBuilder projectionBuilder;
    @Autowired
    private StoryConceptionDraftNormalizer draftNormalizer;

    public ConceptionDraft draftConception(User user, StoryCreateRequest request) {
        Map<String, Object> generated = generateNormalizedDraft(user, request);
        StoryConceptionProjectionBuilder.Projection projection = projectionBuilder.build(request, generated);
        return new ConceptionDraft(
                generated,
                projection.storyUpdate(),
                projection.characterRequests(),
                projection.plotPlanning(),
                projection.outlineSeed()
        );
    }

    private Map<String, Object> generateNormalizedDraft(User user, StoryCreateRequest request) {
        StoryConceptionPromptFactory.PromptContext promptContext = promptFactory.build(request);
        var response = aiService.chat(user, new AiChatRequest(
                    List.of(new AiChatRequest.Message("user", promptContext.prompt())),
                    null,
                    null
            ));
        Map<String, Object> parsed = parseJsonObject(response.content());
        if (parsed == null || parsed.isEmpty()) {
            throw new com.ainovel.app.common.BusinessException("AI_INITIALIZATION_INVALID_RESULT");
        }
        Map<String, Object> normalized = draftNormalizer.normalize(
                request,
                parsed,
                promptContext.hintedPromise(),
                promptContext.hintedTruth(),
                promptContext.hintedMeme()
        );
        List<String> missing = java.util.stream.Stream.of("synopsis", "characters", "skeleton", "twistOptions", "foreshadowSeeds", "memeStrategy", "outlineSuggestion")
                .filter(key -> { Object value=parsed.get(key); return value==null || value instanceof String text && text.isBlank()
                        || value instanceof java.util.Collection<?> items && items.isEmpty() || value instanceof Map<?,?> map && map.isEmpty(); }).toList();
        normalized.put("fallbackFields", missing);
        return normalized;
    }

    private Map<String, Object> parseJsonObject(String text) {
        if (text == null || text.isBlank()) return null;
        String candidate = text.trim();
        if (candidate.startsWith("```")) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                candidate = candidate.substring(first, last + 1);
            }
        }
        try {
            return objectMapper.readValue(candidate, new TypeReference<>() {});
        } catch (Exception ignored) {
            int first = candidate.indexOf('{');
            int last = candidate.lastIndexOf('}');
            if (first >= 0 && last > first) {
                try {
                    return objectMapper.readValue(candidate.substring(first, last + 1), new TypeReference<>() {});
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    public record ConceptionDraft(
            Map<String, Object> generated,
            StoryUpdateRequest storyUpdate,
            List<CharacterRequest> characterRequests,
            Map<String, Object> plotPlanning,
            Map<String, Object> outlineSeed
    ) {
    }
}
