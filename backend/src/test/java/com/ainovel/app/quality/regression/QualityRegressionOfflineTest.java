package com.ainovel.app.quality.regression;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.integration.GrpcChannelFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Manually enabled model regression. It talks to ai-service directly so the run
 * neither reads nor writes AINovel product tables. Provider usage still occurs;
 * never run this test without explicit authorization.
 */
@Tag("offline-quality-regression")
@EnabledIfSystemProperty(named = "qualityRegression.enabled", matches = "true")
class QualityRegressionOfflineTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void evaluateKnownDefectsAndWriteTransientReport() throws Exception {
        long remoteUserId = Long.parseLong(requiredSystemProperty("qualityRegression.remoteUserId"));
        Map<String, String> localConfig = loadLocalConfig();
        ExternalServiceProperties properties = externalProperties(localConfig);
        AiGatewayGrpcClient client = new AiGatewayGrpcClient(properties, new GrpcChannelFactory(properties));

        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        int trueNegative = 0;
        int evidenceHits = 0;
        int correctlyTypedDefects = 0;
        String modelVersion = AiModelPolicy.REQUIRED_TEXT_MODEL_KEY;
        List<Map<String, Object>> rows = new ArrayList<>();

        try {
            for (JsonNode fixture : NovelQualityRegressionCorpusTest.loadCorpus().path("fixtures")) {
                String expected = fixture.path("defectType").asText();
                AiGatewayGrpcClient.ChatResult response = client.chatCompletions(
                        remoteUserId,
                        AiModelPolicy.REQUIRED_TEXT_MODEL_KEY,
                        List.of(
                                new AiChatRequest.Message("system", judgeSystemPrompt()),
                                new AiChatRequest.Message("user", judgeFixturePrompt(fixture))
                        )
                );
                modelVersion = response.modelKey() == null || response.modelKey().isBlank()
                        ? modelVersion
                        : response.modelKey();
                Map<String, Object> parsed = parseJsonObject(response.content());
                String predicted = normalizeDefectType(parsed.get("defectType"));
                List<String> predictedEvidence = stringList(parsed.get("evidence"));
                boolean evidenceHit = evidenceHit(fixture, predictedEvidence);

                if ("NONE".equals(expected)) {
                    if ("NONE".equals(predicted)) {
                        trueNegative++;
                    } else {
                        falsePositive++;
                    }
                } else if (expected.equals(predicted)) {
                    truePositive++;
                    correctlyTypedDefects++;
                    if (evidenceHit) {
                        evidenceHits++;
                    }
                } else {
                    falseNegative++;
                    if (!"NONE".equals(predicted)) {
                        falsePositive++;
                    }
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fixtureId", fixture.path("id").asText());
                row.put("split", fixture.path("split").asText());
                row.put("expectedDefectType", expected);
                row.put("predictedDefectType", predicted);
                row.put("evidence", predictedEvidence);
                row.put("evidenceHit", evidenceHit);
                rows.add(row);
            }
        } finally {
            client.shutdown();
        }

        double precision = ratio(truePositive, truePositive + falsePositive);
        double recall = ratio(truePositive, truePositive + falseNegative);
        double f1 = precision + recall == 0d ? 0d : 2d * precision * recall / (precision + recall);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("generatedAt", Instant.now());
        report.put("modelVersion", modelVersion);
        report.put("fixtureCount", rows.size());
        report.put("truePositive", truePositive);
        report.put("falsePositive", falsePositive);
        report.put("falseNegative", falseNegative);
        report.put("trueNegative", trueNegative);
        report.put("precision", precision);
        report.put("recall", recall);
        report.put("f1", f1);
        report.put("evidenceHitRate", ratio(evidenceHits, correctlyTypedDefects));
        report.put("results", rows);

        Path output = Path.of("target", "quality-regression", "quality-regression-report.json");
        Files.createDirectories(output.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertFalse(rows.isEmpty());
    }

    private static ExternalServiceProperties externalProperties(Map<String, String> config) {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.setProjectKey(config.getOrDefault("EXTERNAL_PROJECT_KEY", "ainovel"));
        properties.setTimeoutMs(longValue(config.get("EXTERNAL_TIMEOUT_MS"), 120000L));
        properties.getAiserviceGrpc().setAddress(required(config, "AI_GRPC_ADDR"));
        properties.getGrpc().setTlsEnabled(booleanValue(config.get("EXTERNAL_GRPC_TLS_ENABLED"), true));
        properties.getGrpc().setPlaintextEnabled(booleanValue(config.get("EXTERNAL_GRPC_PLAINTEXT_ENABLED"), false));
        properties.getSecurity().getAi().setHmacCaller(required(config, "EXTERNAL_AI_HMAC_CALLER"));
        properties.getSecurity().getAi().setHmacSecret(required(config, "EXTERNAL_AI_HMAC_SECRET"));
        return properties;
    }

    private static Map<String, String> loadLocalConfig() throws Exception {
        String configuredPath = requiredSystemProperty("qualityRegression.envFile");
        Path path = Path.of(configuredPath)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalStateException(
                    "qualityRegression.envFile must point to a non-symlink regular file");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(trimmed.substring(0, separator).trim(), unquote(trimmed.substring(separator + 1).trim()));
        }
        return values;
    }

    private static String judgeSystemPrompt() {
        return """
                你是中文小说一致性回归检查器，不评价总体文学水准。只能从下列标签选择一个：
                NONE, SPACE_ENTITY, POV_KNOWLEDGE, MOTIVATION_GOAL, CAUSAL_AFTERSHOCK,
                FORESHADOW_REPEAT, VOICE_STYLE_PROTOCOL。
                只输出 JSON：{"defectType":"...","evidence":["目标正文中的原句"],"reason":"一句话"}。
                没有明确、可引用证据时必须返回 NONE；不得根据作者身份或模型来源推断。
                """;
    }

    private static String judgeFixturePrompt(JsonNode fixture) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("planning", OBJECT_MAPPER.convertValue(fixture.path("context").path("planning"), Object.class));
        input.put("facts", OBJECT_MAPPER.convertValue(fixture.path("context").path("facts"), Object.class));
        input.put("sourceScenes", OBJECT_MAPPER.convertValue(fixture.path("context").path("sourceScenes"), Object.class));
        input.put("targetText", fixture.path("text").asText());
        return "检查目标正文是否存在一项明确缺陷。输入：\n" + OBJECT_MAPPER.writeValueAsString(input);
    }

    private static Map<String, Object> parseJsonObject(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("quality regression judge did not return JSON");
        }
        return OBJECT_MAPPER.readValue(text.substring(start, end + 1), new TypeReference<>() { });
    }

    private static boolean evidenceHit(JsonNode fixture, List<String> predictedEvidence) {
        if (predictedEvidence.isEmpty()) {
            return false;
        }
        for (JsonNode expected : fixture.path("evidence")) {
            String expectedText = expected.path("text").asText();
            for (String predicted : predictedEvidence) {
                if (!predicted.isBlank() && (predicted.contains(expectedText) || expectedText.contains(predicted))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(value -> value != null)
                .map(Object::toString).map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private static String normalizeDefectType(Object raw) {
        String value = raw == null ? "NONE" : raw.toString().trim().toUpperCase(Locale.ROOT);
        return SetHolder.TYPES.contains(value) ? value : "NONE";
    }

    private static double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0d : numerator / (double) denominator;
    }

    private static String requiredSystemProperty(String key) {
        String value = System.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + key);
        }
        return value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.getOrDefault(key, "").trim();
        if (value.isEmpty() || value.startsWith("replace-")) {
            throw new IllegalStateException("Missing usable key in env file: " + key);
        }
        return value;
    }

    private static long longValue(String raw, long fallback) {
        try {
            return raw == null || raw.isBlank() ? fallback : Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean booleanValue(String raw, boolean fallback) {
        return raw == null || raw.isBlank() ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static final class SetHolder {
        private static final java.util.Set<String> TYPES = java.util.Set.of(
                "NONE", "SPACE_ENTITY", "POV_KNOWLEDGE", "MOTIVATION_GOAL",
                "CAUSAL_AFTERSHOCK", "FORESHADOW_REPEAT", "VOICE_STYLE_PROTOCOL"
        );
    }
}
