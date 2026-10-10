package com.ainovel.app.quality.language;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.prompt.AssembledPrompt;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** Immutable prompt profiles; calibration examples are task-specific reference data. */
public final class LanguageStandard {
    public static final String VERSION = "zh-naturalness-v3";
    public enum Task { GENERATE, DIAGNOSE, REVISE, REVIEW }
    public record Selection(String version, boolean generation, boolean diagnosis, String profileHash) {
        public Selection(String version, boolean generation, boolean diagnosis) { this(version,generation,diagnosis,hash(version)); }
        public Selection {
            requireVersion(version);
            if (!hash(version).equals(profileHash)) throw new IllegalStateException("LANGUAGE_PROFILE_CHANGED");
        }
        public boolean replacesLegacy() { return generation || diagnosis; }
        public java.util.Map<String,Object> provenance() {
            return java.util.Map.of("languageProfileVersion",version,"languageProfileHash",profileHash,
                    "languageGenerationEnabled",generation,"languageDiagnosisEnabled",diagnosis);
        }
    }
    private LanguageStandard() {}
    public static void requireVersion(String version) {
        if (!Set.of("zh-naturalness-v1", "zh-naturalness-v2", VERSION).contains(version == null ? "" : version))
            throw new IllegalStateException("LANGUAGE_PROFILE_UNAVAILABLE");
    }
    public static String text() { return text(VERSION); }
    public static String text(String version) { requireVersion(version); return resource(version + ".txt"); }
    private static String resource(String name) {
        try { return new ClassPathResource("quality/language/" + name).getContentAsString(StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new IllegalStateException("LANGUAGE_STANDARD_MISSING", e); }
    }
    /** withExamples=false is for the isolated, frozen comparison harness only. */
    public static String prompt(String version, Task task, boolean withExamples) {
        String rules = text(version);
        if (!VERSION.equals(version)) return rules + switch(task) {
            case GENERATE -> "";
            case DIAGNOSE -> "\n" + LanguageAnalysis.DIAGNOSE;
            case REVISE -> "\n" + LanguageAnalysis.REVISE;
            case REVIEW -> "\n" + LanguageAnalysis.REVIEW;
        };
        try {
            JsonNode profile = new ObjectMapper().readTree(resource(version + ".json"));
            String instruction = profile.path("instructions").path(task.name()).asText();
            if (instruction.isBlank()) throw new IllegalStateException("LANGUAGE_TASK_MISSING");
            StringBuilder result = new StringBuilder(rules).append('\n').append(instruction);
            if (withExamples) {
                result.append("\n以下是校准参考，不是当前作品，不是待检查正文。仅学习判断和表达边界，不复制故事内容。\n<language_examples>\n");
                for (JsonNode example : profile.path("examples")) {
                    if (!example.path("tasks").has(task.name())) continue;
                    var item = new ObjectMapper().createObjectNode();
                    for (String key : new String[]{"id", "source", "status", "facts", "boundaries"}) item.set(key, example.path(key));
                    item.set("reference", example.path("tasks").path(task.name()));
                    result.append(item).append('\n');
                }
                result.append("</language_examples>");
            }
            return result.toString();
        } catch (java.io.IOException e) { throw new IllegalStateException("LANGUAGE_PROFILE_INVALID", e); }
    }
    public static String hash(String version) {
        requireVersion(version);
        StringBuilder all = new StringBuilder();
        for (Task task : Task.values()) all.append(task.name()).append('\n').append(prompt(version, task, true));
        return LanguageProjection.hash(all.toString());
    }
    public static AssembledPrompt augment(AssembledPrompt prompt) {
        return augment(prompt, VERSION, true);
    }
    public static AssembledPrompt augment(AssembledPrompt prompt, String version, boolean withExamples) {
        var messages = new ArrayList<>(prompt.messages());
        messages.add(0, new AiChatRequest.Message("system", prompt(version, Task.GENERATE, withExamples)));
        return new AssembledPrompt(java.util.List.copyOf(messages), prompt.tokenBudget());
    }
}
