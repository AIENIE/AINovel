package com.ainovel.app.prompt;

import java.util.UUID;

public record PromptReference(
        String sourceType,
        UUID sourceId,
        String title,
        String snippet,
        double score,
        int chunkSeq
) {
}
