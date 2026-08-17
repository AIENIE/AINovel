import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Tabs } from "@/components/ui/tabs";
import { api } from "@/lib/api-client";
import type { GenerationRunSummary } from "@/types";
import { GenerationFeedbackPanel } from "./GenerationFeedbackPanel";

const generationRun: GenerationRunSummary = {
  id: "run-1",
  manuscriptId: "manuscript-1",
  sceneId: "scene-1",
  createdBy: "workbench",
  mode: "crafted",
  status: "GENERATED",
  modelKey: "text-premium",
  promptVersion: "scene-draft-v2",
  attemptCount: 1,
  contextHash: "ctx-hash",
  contextManifest: {
    promptVersion: "scene-draft-v2",
    tokenBudget: 3500,
    tokenUsed: 2800,
    sources: [{
      sourceType: "recent_scene",
      sourceId: "scene-0",
      label: "上一场",
      reason: "衔接人物动作",
      estimatedTokens: 420,
      truncated: false,
    }],
  },
  generationVersionId: "version-1",
  previousRunId: null,
  firstEditedAt: "2026-08-12T10:10:00Z",
  lastEditedAt: "2026-08-12T10:20:00Z",
  addedCharacters: 36,
  deletedCharacters: 18,
  retentionRate: 0.87,
  recalculationPending: false,
  feedbackTags: [],
  feedbackNote: "",
  preferenceConfirmed: false,
  createdAt: "2026-08-12T10:00:00Z",
  updatedAt: "2026-08-12T10:20:00Z",
};

describe("GenerationFeedbackPanel", () => {
  afterEach(() => vi.restoreAllMocks());

  it("shows the latest run and saves tags, notes, and long-term preference confirmation", async () => {
    vi.spyOn(api.manuscripts, "listGenerationRuns").mockResolvedValue([generationRun]);
    const updateFeedback = vi.spyOn(api.manuscripts, "updateGenerationRunFeedback").mockResolvedValue({
      ...generationRun,
      feedbackTags: ["PLOT_CAUSALITY"],
      feedbackNote: "保留人物声音",
      preferenceConfirmed: true,
    });

    render(
      <Tabs value="feedback">
        <GenerationFeedbackPanel active manuscriptId="manuscript-1" sceneId="scene-1" latestRunId="run-1" />
      </Tabs>,
    );

    expect(await screen.findByText("87%")).toBeTruthy();
    expect(screen.getByText("衔接人物动作")).toBeTruthy();
    expect(screen.getByText("+36")).toBeTruthy();
    expect(screen.getByText("-18")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "情节因果" }));
    fireEvent.change(screen.getByLabelText("备注"), { target: { value: "保留人物声音" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "确认作为长期偏好样本" }));
    fireEvent.click(screen.getByRole("button", { name: "保存反馈" }));

    await waitFor(() => expect(updateFeedback).toHaveBeenCalledWith(
      "manuscript-1",
      "scene-1",
      "run-1",
      {
        tags: ["PLOT_CAUSALITY"],
        note: "保留人物声音",
        preferenceConfirmed: true,
      },
    ));
  });
});
