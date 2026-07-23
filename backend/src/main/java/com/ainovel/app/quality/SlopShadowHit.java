package com.ainovel.app.quality;

public record SlopShadowHit(
        String patternId,
        String category,
        int occurrenceCount,
        Integer charStart,
        Integer charEnd,
        String evidence
) {
}
