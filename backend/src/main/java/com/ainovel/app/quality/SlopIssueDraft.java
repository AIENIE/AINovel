package com.ainovel.app.quality;

public record SlopIssueDraft(
        SlopDimension dimension,
        SlopSeverity severity,
        int riskScore,
        String evidence,
        String whyItMatters,
        String minimalFix,
        Integer charStart,
        Integer charEnd,
        String quote,
        String module,
        String patternId,
        String issueType,
        String evidenceLevel,
        String alternativeExplanationsJson,
        String repairHint
) {
    public SlopIssueDraft(SlopDimension dimension,
                          SlopSeverity severity,
                          int riskScore,
                          String evidence,
                          String whyItMatters,
                          String minimalFix) {
        this(
                dimension,
                severity,
                riskScore,
                evidence,
                whyItMatters,
                minimalFix,
                null,
                null,
                evidence,
                moduleFor(dimension),
                null,
                dimension == null ? null : dimension.name().toLowerCase(),
                "E1",
                "[]",
                minimalFix
        );
    }

    private static String moduleFor(SlopDimension dimension) {
        if (dimension == null) {
            return "surface_template";
        }
        return switch (dimension) {
            case ARTIFACT -> "consistency_assimilation";
            case LOCAL_COHERENCE -> "breath_focus_pacing";
            case STYLE_DRIFT_LIGHT -> "voice_fit";
            case REPETITION, GENERICITY -> "surface_template";
        };
    }
}
