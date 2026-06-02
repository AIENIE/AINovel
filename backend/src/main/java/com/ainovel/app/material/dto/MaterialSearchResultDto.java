package com.ainovel.app.material.dto;

import java.util.UUID;
import java.util.List;

public record MaterialSearchResultDto(
        UUID materialId,
        String chunkId,
        String title,
        String snippet,
        double score,
        Integer chunkSeq,
        String source,
        List<String> matchReasons
) {}
