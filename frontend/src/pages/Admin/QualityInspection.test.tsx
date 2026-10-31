import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { adminQualityPageSchema } from "@/lib/api-contracts";
import QualityInspection from "./QualityInspection";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("preserves unknown quality scores and an actual assessed zero", async () => {
  const base = { kind: "slop", storyId: "story", manuscriptId: "manuscript", sceneId: "scene", chapterTitle: null, sceneTitle: null, resolved: false, createdAt: null };
  const page = adminQualityPageSchema.parse({ items: [
    { ...base, id: "failed", status: "DEGRADED", summary: "历史失败检查", maxSeverity: null, overallRiskScore: null },
    { ...base, id: "complete", status: "ACCEPTED", summary: "已完成检查", maxSeverity: "LOW", overallRiskScore: 0 },
  ], page: 0, size: 20, totalElements: 2, totalPages: 1 });
  vi.spyOn(api.admin, "listQualityRuns").mockResolvedValue(page);
  render(<QualityInspection />);
  await waitFor(() => expect(screen.getByText("历史失败检查")).toBeTruthy());
  expect(screen.getByText("风险分 —")).toBeTruthy();
  expect(screen.getByText("风险分 0")).toBeTruthy();
  const invalid = structuredClone(page); delete (invalid.items[0] as Partial<typeof invalid.items[0]>).overallRiskScore;
  expect(adminQualityPageSchema.safeParse(invalid).success).toBe(false);
});
