package com.ainovel.app.ai;


@org.springframework.stereotype.Component
public final class AiModelPolicy {
    public static final String REQUIRED_TEXT_MODEL_KEY = "deepseek-v4-flash";
    public static final String REQUIRED_TEXT_MODEL_DISPLAY_NAME = "DeepSeek V4 Flash";

    @org.springframework.beans.factory.annotation.Value("${app.ai.text-model-key:deepseek-v4-flash}")
    private String modelKey = REQUIRED_TEXT_MODEL_KEY;
    @org.springframework.beans.factory.annotation.Value("${app.ai.text-model-display-name:DeepSeek V4 Flash}")
    private String displayName = REQUIRED_TEXT_MODEL_DISPLAY_NAME;

    public AiModelPolicy() {}
    public String modelKey() { return modelKey; }
    public String displayName() { return displayName; }
}
