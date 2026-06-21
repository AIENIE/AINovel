import { describe, expect, it } from "vitest";
import { buildReviewSampleJsonl, evidenceMatrixRows, filterReviewSamples, reviewSampleSummary } from "../quality-review-utils";
import { AdminSlopReviewSample } from "@/types";

const sample = (overrides: Partial<AdminSlopReviewSample>): AdminSlopReviewSample => ({
  id: "sample-1",
  sourceType: "MANUAL",
  sampleId: "P6-001",
  text: "空气仿佛凝固。",
  textPreview: "空气仿佛凝固。",
  genre: "悬疑",
  tone: "冷峻",
  expectedEvidenceLevel: "E1",
  expectedRequiresAiReview: false,
  observedEvidenceLevel: "E1",
  observedRequiresAiReview: false,
  observedRiskScore: 34,
  observedMaxSeverity: "LOW",
  matchesExpected: true,
  status: "PENDING",
  createdBy: "admin",
  ...overrides,
});

describe("quality-review-utils", () => {
  it("filters review samples by status, mismatch and high risk", () => {
    const pending = sample({ id: "pending", status: "PENDING", matchesExpected: true, observedRiskScore: 34 });
    const mismatch = sample({ id: "mismatch", status: "APPROVED", matchesExpected: false, observedRiskScore: 34 });
    const high = sample({ id: "high", status: "PENDING", matchesExpected: true, observedRiskScore: 88 });

    expect(filterReviewSamples([pending, mismatch, high], "", "pending").map((item) => item.id)).toEqual(["pending", "high"]);
    expect(filterReviewSamples([pending, mismatch, high], "", "mismatch").map((item) => item.id)).toEqual(["mismatch"]);
    expect(filterReviewSamples([pending, mismatch, high], "", "high").map((item) => item.id)).toEqual(["high"]);
  });

  it("summarizes sample review state for admin counters", () => {
    const summary = reviewSampleSummary([
      sample({ status: "PENDING", matchesExpected: false, observedRiskScore: 80 }),
      sample({ status: "APPROVED", matchesExpected: true, observedRiskScore: 20 }),
      sample({ status: "NEEDS_DISCUSSION", matchesExpected: false, observedRiskScore: 58 }),
    ]);

    expect(summary.total).toBe(3);
    expect(summary.pending).toBe(1);
    expect(summary.mismatch).toBe(2);
    expect(summary.highRisk).toBe(1);
  });

  it("maps report evidence matrix into stable display rows", () => {
    const rows = evidenceMatrixRows({
      E1: { E1: 2, E2: 1, E3: 0, E4: 0 },
      E2: { E1: 1, E2: 3, E3: 0, E4: 0 },
    });

    expect(rows).toEqual([
      { expected: "E1", E1: 2, E2: 1, E3: 0, E4: 0, total: 3 },
      { expected: "E2", E1: 1, E2: 3, E3: 0, E4: 0, total: 4 },
      { expected: "E3", E1: 0, E2: 0, E3: 0, E4: 0, total: 0 },
      { expected: "E4", E1: 0, E2: 0, E3: 0, E4: 0, total: 0 },
    ]);
  });

  it("builds JSONL import text from sample rows", () => {
    const jsonl = buildReviewSampleJsonl([
      {
        sampleId: "P7-001",
        text: "空气仿佛凝固。",
        expectedEvidenceLevel: "E1",
        expectedRequiresAiReview: false,
        genre: "悬疑",
        tone: "冷峻",
      },
      {
        sampleId: "P7-002",
        text: "以下是修改版：她推开门。",
        expectedEvidenceLevel: "E4",
        expectedRequiresAiReview: true,
      },
    ]);

    expect(jsonl.split("\n")).toEqual([
      "{\"sampleId\":\"P7-001\",\"text\":\"空气仿佛凝固。\",\"expectedEvidenceLevel\":\"E1\",\"expectedRequiresAiReview\":false,\"genre\":\"悬疑\",\"tone\":\"冷峻\"}",
      "{\"sampleId\":\"P7-002\",\"text\":\"以下是修改版：她推开门。\",\"expectedEvidenceLevel\":\"E4\",\"expectedRequiresAiReview\":true}",
    ]);
  });
});
