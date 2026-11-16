package com.ainovel.app.writingagent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

import static com.ainovel.app.writingagent.WritingAgentDtos.*;

final class WritingAgentReviews {
    static final String LANGUAGE_TOOL = "candidate_check_language";
    static final String PLOT_TOOL = "candidate_check_plot";
    static final int MAX_CHECKS_PER_KIND = 4;

    record CheckStep(int sequence, String tool, JsonNode output) {}

    private WritingAgentReviews() {}

    static boolean isCheckTool(String tool) {
        return LANGUAGE_TOOL.equals(tool) || PLOT_TOOL.equals(tool);
    }

    static String kind(String tool) {
        return LANGUAGE_TOOL.equals(tool) ? "LANGUAGE" : "PLOT";
    }

    static int selectedCount(Frozen input) {
        return (input.languageCheck() ? 1 : 0) + (input.plotCheck() ? 1 : 0);
    }

    static CandidateReview summarize(Frozen input, Candidate candidate, List<CheckStep> checks) {
        return new CandidateReview(input.context().minHan(), input.context().maxHan(),
                latest(input.languageCheck(), LANGUAGE_TOOL, candidate, checks),
                latest(input.plotCheck(), PLOT_TOOL, candidate, checks));
    }

    private static CheckReview latest(boolean selected, String tool, Candidate candidate, List<CheckStep> checks) {
        if (!selected) {
            return new CheckReview(CheckStatus.NOT_SELECTED, null);
        }
        for (int index = checks.size() - 1; index >= 0; index--) {
            var step = checks.get(index);
            var output = step.output();
            if (tool.equals(step.tool())
                    && kind(tool).equals(output.path("kind").asText())
                    && candidate.id().toString().equals(output.path("candidateId").asText())
                    && candidate.hash().equals(output.path("candidateHash").asText())) {
                var complete = output.path("report").path("complete");
                var status = complete.isBoolean() && complete.booleanValue() ? CheckStatus.REPORTED : CheckStatus.INCOMPLETE;
                return new CheckReview(status, step.sequence());
            }
        }
        return new CheckReview(CheckStatus.PENDING, null);
    }
}
