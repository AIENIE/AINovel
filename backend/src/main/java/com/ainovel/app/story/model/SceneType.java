package com.ainovel.app.story.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SceneType {
    ACTION("action"),
    DIALOGUE("dialogue"),
    INTROSPECTION("introspection"),
    DESCRIPTION("description"),
    FLASHBACK("flashback");

    private final String value;

    SceneType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<SceneType> parse(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.toString().trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.value.equals(normalized)).findFirst();
    }

    public static boolean isExplicitlySupported(Object raw) {
        return parse(raw).isPresent();
    }
}
