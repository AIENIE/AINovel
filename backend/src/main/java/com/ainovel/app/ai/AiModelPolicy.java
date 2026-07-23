package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiModelDto;

public final class AiModelPolicy {
    public static final String REQUIRED_TEXT_MODEL_KEY = "deepseek-v4-flash";
    public static final String REQUIRED_TEXT_MODEL_DISPLAY_NAME = "DeepSeek V4 Flash";
    public static final String REQUIRED_TEXT_MODEL_PROVIDER = "DeepSeek";

    private AiModelPolicy() {
    }

    public static AiModelDto requiredTextModel() {
        return new AiModelDto(
                REQUIRED_TEXT_MODEL_KEY,
                REQUIRED_TEXT_MODEL_KEY,
                REQUIRED_TEXT_MODEL_DISPLAY_NAME,
                "text",
                1,
                1,
                REQUIRED_TEXT_MODEL_PROVIDER,
                true,
                false,
                true
        );
    }
}
