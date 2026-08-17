package com.ainovel.app.manuscript.attribution;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Allowlists provenance metadata. Prompt messages, context prose and generated
 * text are deliberately not accepted by this manifest boundary.
 */
public final class SceneGenerationManifest {
    private static final Set<String> SCALAR_KEYS = Set.of(
            "schemaVersion",
            "mode",
            "sceneId",
            "generator",
            "modelKey",
            "model",
            "modelProvider",
            "promptVersion",
            "promptHash",
            "attemptCount",
            "contextHash",
            "tokenBudget",
            "tokenUsed",
            "templateVersion",
            "promptTemplateId",
            "promptTemplateVersion",
            "contextSnapshotId",
            "contextSnapshotHash",
            "contextTokenEstimate",
            "contextBudget",
            "qualityRunId",
            "requestedAt"
    );
    private static final Set<String> LIST_KEYS = Set.of(
            "contextEntryIds",
            "contextSectionIds",
            "contextHashes"
    );
    private static final Set<String> PARAMETER_KEYS = Set.of(
            "temperature",
            "topP",
            "topK",
            "minP",
            "maxTokens",
            "seed",
            "frequencyPenalty",
            "presencePenalty"
    );

    private SceneGenerationManifest() {}

    public static Map<String, Object> sanitize(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (SCALAR_KEYS.contains(key)) {
                Object scalar = scalar(entry.getValue());
                if (scalar != null) {
                    sanitized.put(key, scalar);
                }
            } else if (LIST_KEYS.contains(key) && entry.getValue() instanceof Iterable<?> values) {
                List<Object> items = new ArrayList<>();
                for (Object value : values) {
                    Object scalar = scalar(value);
                    if (scalar != null && items.size() < 256) {
                        items.add(scalar);
                    }
                }
                sanitized.put(key, List.copyOf(items));
            } else if ("sources".equals(key) && entry.getValue() instanceof Iterable<?> values) {
                sanitized.put(key, sanitizeSources(values));
            } else if ("generationParameters".equals(key) && entry.getValue() instanceof Map<?, ?> values) {
                Map<String, Object> parameters = new LinkedHashMap<>();
                for (Map.Entry<?, ?> value : values.entrySet()) {
                    String parameterKey = String.valueOf(value.getKey());
                    if (!PARAMETER_KEYS.contains(parameterKey)) {
                        continue;
                    }
                    Object scalar = scalar(value.getValue());
                    if (scalar instanceof Number || scalar instanceof Boolean) {
                        parameters.put(parameterKey, scalar);
                    }
                }
                sanitized.put(key, Map.copyOf(parameters));
            }
        }
        return Map.copyOf(sanitized);
    }

    private static List<Map<String, Object>> sanitizeSources(Iterable<?> values) {
        Set<String> allowed = Set.of(
                "sourceType", "sourceId", "label", "reason", "estimatedTokens", "truncated"
        );
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw) || sources.size() >= 256) {
                continue;
            }
            Map<String, Object> source = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!allowed.contains(key)) {
                    continue;
                }
                Object scalar = scalar(entry.getValue());
                if (scalar != null) {
                    source.put(key, scalar);
                }
            }
            sources.add(Map.copyOf(source));
        }
        return List.copyOf(sources);
    }

    private static Object scalar(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof UUID || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString();
            return text.length() <= 500 ? text : text.substring(0, 500);
        }
        return null;
    }
}
