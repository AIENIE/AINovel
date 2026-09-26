package com.ainovel.app.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Field-level contracts for destructive branch operations. Missing and explicit null differ. */
public final class V2BranchRequests {
    private V2BranchRequests() { }

    public enum MergeStrategy { REPLACE_ALL, SCENE_SELECT }
    public enum Resolution {
        TARGET, SOURCE;
        @JsonValue public String wireValue() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum BranchStatus {
        ACTIVE, ABANDONED, MERGED;
        @JsonValue public String wireValue() { return name().toLowerCase(Locale.ROOT); }
    }

    public record CreateBranch(String name, String description, UUID sourceVersionId) {
        public CreateBranch {
            name = requiredName(name);
            description = description == null ? "" : description;
        }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static CreateBranch fromJson(JsonNode body) {
            validateFields(body, Set.of("name", "description", "sourceVersionId"));
            String source = text(body, "sourceVersionId", null);
            return new CreateBranch(text(body, "name", null), text(body, "description", ""),
                    source == null ? null : UUID.fromString(source));
        }
    }

    public record UpdateBranch(String name, String description, BranchStatus status) {
        public UpdateBranch { if (name != null) name = requiredName(name); }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static UpdateBranch fromJson(JsonNode body) {
            validateFields(body, Set.of("name", "description", "status"));
            String state = text(body, "status", null);
            return new UpdateBranch(text(body, "name", null), text(body, "description", null),
                    state == null ? null : BranchStatus.valueOf(state.toUpperCase(Locale.ROOT)));
        }
    }

    public record MergeBranch(MergeStrategy strategy, Map<String, Resolution> sceneResolutions, String label) {
        public MergeBranch {
            if (strategy == null || sceneResolutions == null) throw new IllegalArgumentException("INVALID_BRANCH_REQUEST");
            sceneResolutions = Map.copyOf(sceneResolutions);
            if (label != null && (label.isBlank() || label.length() > 200)) throw new IllegalArgumentException("INVALID_BRANCH_LABEL");
        }
        public static MergeBranch defaults() { return new MergeBranch(MergeStrategy.REPLACE_ALL, Map.of(), null); }
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static MergeBranch fromJson(JsonNode body) {
            validateFields(body, Set.of("strategy", "sceneResolutions", "label"));
            MergeStrategy strategy = MergeStrategy.valueOf(text(body, "strategy", "REPLACE_ALL").toUpperCase(Locale.ROOT));
            Map<String, Resolution> resolutions = new LinkedHashMap<>();
            if (body.has("sceneResolutions")) {
                JsonNode raw = body.get("sceneResolutions");
                if (!raw.isObject()) throw new IllegalArgumentException("INVALID_SCENE_RESOLUTIONS");
                raw.fields().forEachRemaining(entry -> {
                    UUID.fromString(entry.getKey());
                    if (!entry.getValue().isTextual()) throw new IllegalArgumentException("INVALID_SCENE_RESOLUTION");
                    resolutions.put(entry.getKey(), Resolution.valueOf(entry.getValue().textValue().toUpperCase(Locale.ROOT)));
                });
            }
            return new MergeBranch(strategy, resolutions, text(body, "label", null));
        }
    }

    private static void validateFields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("INVALID_BRANCH_REQUEST");
        body.fields().forEachRemaining(entry -> {
            if (!allowed.contains(entry.getKey()) || entry.getValue().isNull()) {
                throw new IllegalArgumentException("INVALID_BRANCH_REQUEST");
            }
        });
    }

    private static String text(JsonNode body, String key, String fallback) {
        JsonNode value = body.get(key);
        if (value == null) return fallback;
        if (!value.isTextual()) throw new IllegalArgumentException("INVALID_BRANCH_REQUEST");
        return value.textValue();
    }

    private static String requiredName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("INVALID_BRANCH_NAME");
        return value.trim();
    }
}
