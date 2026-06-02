package com.ainovel.app.prompt;

import com.ainovel.app.ai.dto.AiChatRequest;

import java.util.List;

public record AssembledPrompt(
        List<AiChatRequest.Message> messages,
        int tokenBudget
) {
}
