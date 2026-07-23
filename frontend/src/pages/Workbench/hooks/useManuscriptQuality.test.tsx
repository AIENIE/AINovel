import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { createQueryClientWrapper, createTestQueryClient } from "@/test/queryClient";
import { useManuscriptQuality } from "./useManuscriptQuality";

describe("useManuscriptQuality", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the latest slop and plot runs for the selected scene", async () => {
    vi.spyOn(api.v2.quality, "listRuns").mockResolvedValue([{ id: "quality-1" }] as any);
    vi.spyOn(api.v2.plotQuality, "listRuns").mockResolvedValue([{ id: "plot-1" }] as any);
    vi.spyOn(api.v2.plotQuality, "getTrend").mockResolvedValue({ points: [], dimensionCounts: { CAUSALITY: 2 } } as any);

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptQuality({
          applyServerSection: vi.fn(),
          content: "",
          dirtyScenes: {},
          persistSection: vi.fn(),
          selectedManuscriptId: "manuscript-1",
          selectedSceneId: "scene-1",
          setSidebarTab: vi.fn(),
          toast: vi.fn(),
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(result.current.qualityRunsByScene["scene-1"]).toEqual({ id: "quality-1" });
    });

    expect(result.current.plotRunsByScene["scene-1"]).toEqual({ id: "plot-1" });
    expect(result.current.plotTrend).toEqual({ points: [], dimensionCounts: { CAUSALITY: 2 } });
  });

  it("persists dirty content before running plot diagnosis", async () => {
    vi.spyOn(api.v2.quality, "listRuns").mockResolvedValue([] as any);
    vi.spyOn(api.v2.plotQuality, "listRuns")
      .mockResolvedValueOnce([] as any)
      .mockResolvedValueOnce([{ id: "plot-run-2", overallRiskScore: 18 }] as any);
    vi.spyOn(api.v2.plotQuality, "getTrend").mockResolvedValue({ points: [], dimensionCounts: {} } as any);
    vi.spyOn(api.v2.plotQuality, "startAnalyzeScene").mockResolvedValue({ operationId: "operation-2" });
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (_id, onProgress) => {
      const completed = {
        id: "operation-2", operationType: "AINOVEL_LONG_TASK", status: "SUCCEEDED",
        totalSteps: 2, completedSteps: 2, remainingSteps: 0,
        currentStepOutputTokens: 128, outputTokensEstimated: false, attemptCount: 1,
      } as any;
      onProgress(completed);
      return completed;
    });
    const persistSection = vi.fn().mockResolvedValue(undefined);
    const setSidebarTab = vi.fn();
    const toast = vi.fn();

    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const { result } = renderHook(
      () =>
        useManuscriptQuality({
          applyServerSection: vi.fn(),
          content: "<p>draft scene</p>",
          dirtyScenes: { "scene-2": true },
          persistSection,
          selectedManuscriptId: "manuscript-2",
          selectedSceneId: "scene-2",
          setSidebarTab,
          toast,
        }),
      { wrapper },
    );

    await act(async () => {
      await result.current.runPlotDiagnosis();
    });

    expect(persistSection).toHaveBeenCalledWith("scene-2", "<p>draft scene</p>", true);
    expect(api.v2.plotQuality.startAnalyzeScene).toHaveBeenCalledWith("manuscript-2", "scene-2");
    expect(setSidebarTab).toHaveBeenCalledWith("plot");
    expect(result.current.plotRunsByScene["scene-2"]).toEqual({ id: "plot-run-2", overallRiskScore: 18 });
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "剧情诊断已完成" }));
  });

  it("reuses cached quality queries when the same scene remounts", async () => {
    const qualityRunsSpy = vi.spyOn(api.v2.quality, "listRuns").mockResolvedValue([{ id: "quality-1" }] as any);
    const plotRunsSpy = vi.spyOn(api.v2.plotQuality, "listRuns").mockResolvedValue([{ id: "plot-1" }] as any);
    const trendSpy = vi.spyOn(api.v2.plotQuality, "getTrend").mockResolvedValue({ points: [], dimensionCounts: {} } as any);
    const queryClient = createTestQueryClient();
    const wrapper = createQueryClientWrapper(queryClient);
    const options = {
      applyServerSection: vi.fn(),
      content: "",
      dirtyScenes: {},
      persistSection: vi.fn(),
      selectedManuscriptId: "manuscript-1",
      selectedSceneId: "scene-1",
      setSidebarTab: vi.fn(),
      toast: vi.fn(),
    };

    const firstRender = renderHook(() => useManuscriptQuality(options), { wrapper });

    await waitFor(() => {
      expect(firstRender.result.current.selectedQualityRun).toEqual({ id: "quality-1" });
    });

    firstRender.unmount();

    const secondRender = renderHook(() => useManuscriptQuality(options), { wrapper });

    await waitFor(() => {
      expect(secondRender.result.current.selectedQualityRun).toEqual({ id: "quality-1" });
    });

    expect(qualityRunsSpy).toHaveBeenCalledTimes(1);
    expect(plotRunsSpy).toHaveBeenCalledTimes(1);
    expect(trendSpy).toHaveBeenCalledTimes(1);
  });
});
