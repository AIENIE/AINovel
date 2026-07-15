package com.ainovel.app.workflow.model;

public enum GuidedCreationStep {
    PREMISE,
    WORLD,
    CHARACTERS,
    OUTLINE,
    COMPLETED;

    public GuidedCreationStep next() {
        return switch (this) {
            case PREMISE -> WORLD;
            case WORLD -> CHARACTERS;
            case CHARACTERS -> OUTLINE;
            case OUTLINE, COMPLETED -> COMPLETED;
        };
    }
}
