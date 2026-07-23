package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class SlopPatternRegistry {
    static final String INDEX_RESOURCE = "quality/slop-patterns/index.json";
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> CATEGORIES = Set.of(
            "NEGATIVE_PARALLELISM", "DIALOGUE_TAIL", "BODY_ACTION", "IMAGERY",
            "ABSTRACT_EMOTION", "OPENING_CLICHE", "ENDING_CLICHE", "ARTIFACT", "PHRASE", "NARRATIVE_MECHANIC",
            "SHADOW_RULE_OF_THREE", "SHADOW_UNIFORM_LENGTH", "SHADOW_LEXICAL_VARIATION",
            "SHADOW_CONNECTOR_DENSITY", "SHADOW_MODEL_STYLE_HYPOTHESIS"
    );
    private static final Set<String> GENERATION_CATEGORIES = Set.of(
            "PHRASE", "BODY_ACTION", "IMAGERY", "ENDING_CLICHE", "NARRATIVE_MECHANIC"
    );
    private static final Set<String> SHADOW_DETECTORS = Set.of(
            "RULE_OF_THREE", "UNIFORM_SENTENCE_LENGTH", "LOW_LEXICAL_VARIATION", "CONNECTOR_DENSITY"
    );

    private final SlopPatternCatalog catalog;
    private final List<SlopPatternRule> rules;
    private final Map<String, List<Pattern>> compiledRegexes;

    public SlopPatternRegistry() {
        this(Thread.currentThread().getContextClassLoader(), new ObjectMapper());
    }

    SlopPatternRegistry(ClassLoader classLoader, ObjectMapper objectMapper) {
        try {
            catalog = read(classLoader, objectMapper, INDEX_RESOURCE, SlopPatternCatalog.class);
            validateCatalog(catalog);
            List<SlopPatternRule> loaded = new ArrayList<>();
            for (String resource : catalog.resources()) {
                SlopPatternResource file = read(classLoader, objectMapper, resource, SlopPatternResource.class);
                if (file.schemaVersion() != SCHEMA_VERSION) {
                    throw invalid(resource + " schemaVersion must be " + SCHEMA_VERSION);
                }
                loaded.addAll(file.rules());
            }
            compiledRegexes = validateRules(loaded);
            rules = List.copyOf(loaded);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load slop pattern registry", ex);
        }
    }

    public List<SlopPatternRule> activeRules() {
        return rules.stream().filter(rule -> rule.status() == SlopPatternStatus.ACTIVE).toList();
    }

    public List<SlopPatternRule> shadowRules() {
        return rules.stream().filter(rule -> rule.status() == SlopPatternStatus.SHADOW).toList();
    }

    List<SlopGenerationConstraint> generationConstraints() {
        return rules.stream()
                .filter(rule -> rule.generation() != null && rule.generation().enabled())
                .map(rule -> new SlopGenerationConstraint(
                        rule.id(), rule.generation().category(), rule.generation().promptText(), rule.generation().weight()))
                .toList();
    }

    List<Pattern> compiledRegexes(SlopPatternRule rule) {
        return compiledRegexes.getOrDefault(rule.id(), List.of());
    }

    int windowChars() { return catalog.windowChars(); }
    int sameFamilyMediumCount() { return catalog.sameFamilyMediumCount(); }
    int sameFamilyHighCount() { return catalog.sameFamilyHighCount(); }
    int categoryMediumCount() { return catalog.categoryMediumCount(); }
    int categoryHighCount() { return catalog.categoryHighCount(); }
    int issueCap() { return catalog.issueCap(); }

    private Map<String, List<Pattern>> validateRules(List<SlopPatternRule> loaded) {
        if (loaded.isEmpty()) {
            throw invalid("registry has no rules");
        }
        Set<String> ids = new HashSet<>();
        Map<String, List<Pattern>> compiled = new HashMap<>();
        for (SlopPatternRule rule : loaded) {
            if (rule.id() == null || !rule.id().matches("[A-Z0-9_]{4,80}")) {
                throw invalid("invalid rule id: " + rule.id());
            }
            if (!ids.add(rule.id())) {
                throw invalid("duplicate rule id: " + rule.id());
            }
            if (rule.status() == null || !EnumSet.allOf(SlopPatternStatus.class).contains(rule.status())) {
                throw invalid("invalid status for " + rule.id());
            }
            if (!CATEGORIES.contains(rule.category())) {
                throw invalid("invalid category for " + rule.id() + ": " + rule.category());
            }
            if (rule.status() == SlopPatternStatus.ACTIVE) {
                if (rule.literals().isEmpty() && rule.regexes().isEmpty()) {
                    throw invalid("ACTIVE rule must have literal or regex matchers: " + rule.id());
                }
                if (rule.dimension() == null || blank(rule.module()) || blank(rule.whyItMatters()) || blank(rule.repairHint())) {
                    throw invalid("ACTIVE rule lacks issue metadata: " + rule.id());
                }
                if (rule.positiveSamples().isEmpty() || rule.contextualSamples().isEmpty()) {
                    throw invalid("ACTIVE rule needs positive and contextual samples: " + rule.id());
                }
            }
            if (rule.status() == SlopPatternStatus.SHADOW && !rule.hasMatcher()) {
                throw invalid("SHADOW rule must have matcher or detector: " + rule.id());
            }
            if (rule.detector() != null && !rule.detector().isBlank() && !SHADOW_DETECTORS.contains(rule.detector())) {
                throw invalid("unknown shadow detector for " + rule.id() + ": " + rule.detector());
            }
            if (rule.literals().stream().anyMatch(SlopPatternRegistry::blank)) {
                throw invalid("blank literal matcher for " + rule.id());
            }
            SlopPatternRule.Generation generation = rule.generation();
            if (generation != null && generation.enabled()) {
                if (!GENERATION_CATEGORIES.contains(generation.category()) || blank(generation.promptText())
                        || generation.weight() < 1 || generation.weight() > 5) {
                    throw invalid("invalid generation constraint: " + rule.id());
                }
            } else if (rule.status() == SlopPatternStatus.GENERATION_ONLY) {
                throw invalid("GENERATION_ONLY rule must enable generation: " + rule.id());
            }
            List<Pattern> patterns = new ArrayList<>();
            for (String regex : rule.regexes()) {
                validateBoundedRegex(rule.id(), regex);
                try {
                    int flags = Pattern.UNICODE_CASE;
                    if (rule.ignoreCase()) flags |= Pattern.CASE_INSENSITIVE;
                    patterns.add(Pattern.compile(regex, flags));
                } catch (PatternSyntaxException ex) {
                    throw invalid("invalid regex for " + rule.id() + ": " + ex.getMessage());
                }
            }
            compiled.put(rule.id(), List.copyOf(patterns));
        }
        return Map.copyOf(compiled);
    }

    private void validateCatalog(SlopPatternCatalog value) {
        if (value.schemaVersion() != SCHEMA_VERSION) {
            throw invalid("index schemaVersion must be " + SCHEMA_VERSION);
        }
        if (value.resources().isEmpty() || value.windowChars() <= 0 || value.issueCap() <= 0
                || value.sameFamilyMediumCount() <= 0
                || value.sameFamilyMediumCount() >= value.sameFamilyHighCount()
                || value.categoryMediumCount() <= 0
                || value.categoryMediumCount() >= value.categoryHighCount()) {
            throw invalid("catalog thresholds must be positive and monotonic");
        }
    }

    private void validateBoundedRegex(String id, String regex) {
        if (blank(regex) || regex.contains(".*") || regex.contains(".+")
                || regex.matches(".*\\{\\d+,}.*")) {
            throw invalid("regex must be explicitly bounded for " + id + ": " + regex);
        }
    }

    private static <T> T read(ClassLoader loader, ObjectMapper mapper, String resource, Class<T> type) throws IOException {
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing slop pattern resource: " + resource);
            }
            return mapper.readValue(stream, type);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid slop pattern registry: " + message);
    }
}
