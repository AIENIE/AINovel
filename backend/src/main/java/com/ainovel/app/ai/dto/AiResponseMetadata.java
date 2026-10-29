package com.ainovel.app.ai.dto;

import java.util.Locale;

/** Safe provider outcome metadata. Missing fields remain unknown; private reasoning is never included. */
public record AiResponseMetadata(int version, FinishReason finishReason, Long reasoningTokens,
                                 Boolean usageAuthoritative) {
    public enum FinishReason { STOP, LENGTH, CONTENT_FILTER, TOOL_CALLS, OTHER, UNKNOWN }

    public AiResponseMetadata {
        if (version != 1) throw new IllegalArgumentException("Unsupported AI response metadata version");
        finishReason = finishReason == null ? FinishReason.UNKNOWN : finishReason;
        if (reasoningTokens != null && reasoningTokens < 0) reasoningTokens = null;
    }

    public static AiResponseMetadata reported(String reason, Long reasoningTokens, Boolean authoritative) {
        FinishReason normalized = reason == null || reason.isBlank() ? FinishReason.UNKNOWN : switch (reason.strip().toLowerCase(Locale.ROOT)) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            case "tool_calls", "function_call" -> FinishReason.TOOL_CALLS;
            default -> FinishReason.OTHER;
        };
        if (normalized == FinishReason.UNKNOWN && reasoningTokens == null && authoritative == null) return null;
        return new AiResponseMetadata(1, normalized, reasoningTokens, authoritative);
    }
}
