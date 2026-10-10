package com.ainovel.app.material.evidence;

import java.util.UUID;

public record MaterialGenerationCompleted(
        UUID ownerId, UUID manuscriptId, UUID sceneId, UUID bodyVersion) {}
