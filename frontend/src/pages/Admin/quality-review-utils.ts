import { AdminSlopReviewSample } from "@/types";

export type ReviewSampleFilter = "all" | "pending" | "mismatch" | "high";

export const filterReviewSamples = (
  samples: AdminSlopReviewSample[],
  search: string,
  filter: ReviewSampleFilter
) => {
  const keyword = search.trim().toLowerCase();
  return samples
    .filter((sample) => {
      if (!keyword) return true;
      const haystack = [
        sample.sampleId,
        sample.text,
        sample.textPreview,
        sample.genre,
        sample.tone,
        sample.expectedEvidenceLevel,
        sample.observedEvidenceLevel,
        sample.status,
        sample.reviewerNote,
        sample.sourceRunId,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(keyword);
    })
    .filter((sample) => {
      if (filter === "pending") return sample.status === "PENDING";
      if (filter === "mismatch") return !sample.matchesExpected;
      if (filter === "high") return Number(sample.observedRiskScore ?? 0) >= 70 || ["HIGH", "BLOCKING"].includes(sample.observedMaxSeverity);
      return true;
    });
};

export const reviewSampleSummary = (samples: AdminSlopReviewSample[]) => ({
  total: samples.length,
  pending: samples.filter((sample) => sample.status === "PENDING").length,
  mismatch: samples.filter((sample) => !sample.matchesExpected).length,
  highRisk: samples.filter((sample) => Number(sample.observedRiskScore ?? 0) >= 70 || ["HIGH", "BLOCKING"].includes(sample.observedMaxSeverity)).length,
});
