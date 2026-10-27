package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiResponseMetadata;

/** Classifies unusable setting results using reported response facts, never a model-name guess. */
final class WorldAssistantResultFailure {
    static final String EMPTY = "WORLD_ASSISTANT_EMPTY_RESULT";
    static final String OUTPUT_LIMIT = "WORLD_ASSISTANT_OUTPUT_LIMIT";
    static final String REASONING_LIMIT = "WORLD_ASSISTANT_REASONING_OUTPUT_LIMIT";
    static final String TRUNCATED = "WORLD_ASSISTANT_TRUNCATED_RESULT";
    static final String INVALID = "WORLD_ASSISTANT_RESULT_INVALID";

    private WorldAssistantResultFailure() {}

    static String code(AiChatResponse response) {
        boolean empty = response.content() == null || response.content().isBlank();
        var metadata = response.metadata();
        boolean length = metadata != null && metadata.finishReason() == AiResponseMetadata.FinishReason.LENGTH;
        if (!empty) return length ? TRUNCATED : INVALID;
        if (!length) return EMPTY;
        if (Boolean.TRUE.equals(metadata.usageAuthoritative()) && metadata.reasoningTokens() != null
                && metadata.reasoningTokens() > 0 && response.usage() != null
                && Double.compare(metadata.reasoningTokens().doubleValue(),response.usage().outputTokens()) == 0) return REASONING_LIMIT;
        return OUTPUT_LIMIT;
    }

    static boolean classifiable(String code) {
        return INVALID.equals(code) || EMPTY.equals(code) || OUTPUT_LIMIT.equals(code)
                || REASONING_LIMIT.equals(code) || TRUNCATED.equals(code);
    }
}
