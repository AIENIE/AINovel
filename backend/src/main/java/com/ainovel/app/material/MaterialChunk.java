package com.ainovel.app.material;

import java.util.UUID;

public record MaterialChunk(
        String chunkId,
        UUID materialId,
        String title,
        String text,
        int chunkSeq,
        String tags
) {
}
