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

const evidenceLevels = ["E1", "E2", "E3", "E4"] as const;

export type EvidenceLevel = (typeof evidenceLevels)[number];

export type EvidenceMatrix = Partial<Record<EvidenceLevel, Partial<Record<EvidenceLevel, number>>>>;

export const evidenceMatrixRows = (matrix: EvidenceMatrix = {}) =>
  evidenceLevels.map((expected) => {
    const row = matrix[expected] || {};
    const values = {
      E1: Number(row.E1 ?? 0),
      E2: Number(row.E2 ?? 0),
      E3: Number(row.E3 ?? 0),
      E4: Number(row.E4 ?? 0),
    };
    return {
      expected,
      ...values,
      total: values.E1 + values.E2 + values.E3 + values.E4,
    };
  });

export type ReviewSampleImportRow = {
  sampleId?: string;
  text: string;
  expectedEvidenceLevel: string;
  expectedRequiresAiReview: boolean;
  genre?: string;
  tone?: string;
  characterContext?: string;
  styleContext?: string;
  reviewerNote?: string;
};

export const buildReviewSampleJsonl = (rows: ReviewSampleImportRow[]) =>
  rows
    .map((row) => {
      const cleaned = Object.fromEntries(
        Object.entries(row).filter(([, value]) => value !== undefined && value !== "")
      );
      return JSON.stringify(cleaned);
    })
    .join("\n");
