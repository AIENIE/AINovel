package com.ainovel.app.material.evidence;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/** A model finding without a verifiable quotation can only remain undecided. */
public final class EvidenceReportValidator {
    private EvidenceReportValidator() {}

    public static void validateAnalysis(JsonNode report, Map<String, String> opened) {
        var candidates = report.path("candidates");
        if (!candidates.isArray() || candidates.size() > 30)
            throw new IllegalArgumentException("MATERIAL_ANALYSIS_INVALID");
        for (var candidate : candidates) {
            if (!Set.of("ENTITY", "TAG", "FACT").contains(candidate.path("type").asText()))
                throw new IllegalArgumentException("MATERIAL_ANALYSIS_TYPE_INVALID");
            if (candidate.path("type").asText().equals("FACT")
                    && candidate.path("statement").asText().isBlank())
                throw new IllegalArgumentException("MATERIAL_FACT_REQUIRED");
            validateEvidence(candidate.path("evidence"), opened, true);
        }
    }

    public static void validate(JsonNode report, Map<String, String> opened) {
        var findings = report.path("findings");
        if (!findings.isArray() || findings.size() > 12)
            throw new IllegalArgumentException("EVIDENCE_REPORT_INVALID");
        for (var finding : findings) {
            String decision = finding.path("decision").asText();
            if (!Set.of("SUPPORTED", "CONTRADICTED", "INSUFFICIENT", "UNKNOWN").contains(decision))
                throw new IllegalArgumentException("EVIDENCE_DECISION_INVALID");
            validateEvidence(
                    finding.path("evidence"),
                    opened,
                    Set.of("SUPPORTED", "CONTRADICTED").contains(decision));
        }
    }

    private static void validateEvidence(
            JsonNode evidence, Map<String, String> opened, boolean required) {
        if (!evidence.isArray() || evidence.size() > 12)
            throw new IllegalArgumentException("EVIDENCE_CITATIONS_INVALID");
        if (required && evidence.isEmpty())
            throw new IllegalArgumentException("EVIDENCE_REQUIRED_FOR_DECISION");
        for (var quote : evidence) {
            String source = opened.get(quote.path("sourceId").asText());
            var start = quote.path("start");
            var end = quote.path("end");
            if (source == null
                    || !start.isIntegralNumber()
                    || !end.isIntegralNumber()
                    || !start.canConvertToInt()
                    || !end.canConvertToInt()
                    || quote.path("quote").asText().isBlank()
                    || !EvidenceText.slice(source, start.intValue(), end.intValue())
                            .equals(quote.path("quote").asText()))
                throw new IllegalArgumentException("EVIDENCE_QUOTE_MISMATCH");
        }
    }
}
