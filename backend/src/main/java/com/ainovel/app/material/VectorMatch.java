package com.ainovel.app.material;

public record VectorMatch(
        String chunkId,
        double score,
        java.util.UUID materialId,
        String title,
        String text,
        int chunkSeq
) {
}
