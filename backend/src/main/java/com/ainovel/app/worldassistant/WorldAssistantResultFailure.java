package com.ainovel.app.worldassistant;

import com.ainovel.app.ai.dto.AiChatResponse;

/** Classifies unusable setting results using reported response facts, never a model-name guess. */
final class WorldAssistantResultFailure {
    static final String EMPTY = "WORLD_ASSISTANT_EMPTY_RESULT";
    static final String OUTPUT_LIMIT = "WORLD_ASSISTANT_OUTPUT_LIMIT";
    static final String REASONING_LIMIT = "WORLD_ASSISTANT_REASONING_OUTPUT_LIMIT";
    static final String TRUNCATED = "WORLD_ASSISTANT_TRUNCATED_RESULT";
    static final String INVALID = "WORLD_ASSISTANT_RESULT_INVALID";

    private WorldAssistantResultFailure() {}

    static String code(AiChatResponse response) {
        return "WORLD_ASSISTANT_"+com.ainovel.app.ai.AiResultDiagnostics.unusable(response).name();
    }

    static boolean classifiable(String code) {
        return INVALID.equals(code) || EMPTY.equals(code) || OUTPUT_LIMIT.equals(code)
                || REASONING_LIMIT.equals(code) || TRUNCATED.equals(code);
    }
}
