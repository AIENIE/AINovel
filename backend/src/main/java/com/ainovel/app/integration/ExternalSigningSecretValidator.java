package com.ainovel.app.integration;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Validates the exact UTF-8 bytes used at external signing boundaries. */
public final class ExternalSigningSecretValidator {
    private static final int MIN_BYTES = 32;
    private static final int MAX_BYTES = 4_096;

    private ExternalSigningSecretValidator() {
    }

    public static void validate(String value, String boundary) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (value == null
                || !value.equals(value.trim())
                || value.codePoints().anyMatch(codePoint ->
                        Character.isWhitespace(codePoint)
                                || Character.isSpaceChar(codePoint)
                                || Character.isISOControl(codePoint)
                                || Character.getType(codePoint) == Character.FORMAT)
                || bytes.length < MIN_BYTES
                || bytes.length > MAX_BYTES
                || value.codePoints().distinct().count() < 8
                || isPlaceholder(value)) {
            throw new IllegalArgumentException(boundary + " secret must contain 32..4096 exact UTF-8 bytes");
        }
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty()
                || normalized.startsWith("REPLACE_ME")
                || normalized.contains("REPLACE-WITH")
                || normalized.contains("REPLACE_WITH")
                || normalized.contains("CHANGE-ME")
                || normalized.contains("CHANGE_ME")
                || normalized.contains("CHANGEME")
                || normalized.contains("PLACEHOLDER")
                || normalized.contains("EXAMPLE")
                || normalized.contains("FIXTURE")
                || normalized.contains("DUMMY")
                || normalized.contains("PASSWORD")
                || normalized.contains("SAMPLE")
                || normalized.matches(".*\\$\\{[^}\\r\\n]+}.*")
                || normalized.matches("<[^>]+>");
    }
}
