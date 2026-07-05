import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useManuscriptQuality } from "./useManuscriptQuality";

describe("useManuscriptQuality", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the latest slop and plot runs for the selected scene", async () => {
    vi.spyOn(api.v2.quality, "listRuns").mockResolvedValue([{ id: "quality-1" }] as any);
    vi.spyOn(api.v2.plotQuality, "listRuns").mockResolvedValue([{ id: "plot-1" }] as any);
    vi.spyOn(api.v2.plotQuality, "getTrend").mockResolvedValue({ points: [], dimensionCounts: { CAUSALITY: 2 } } as any);

    const { result } = renderHook(() =>
      useManuscriptQuality({
        applyFetchedManuscript: vi.fn(),
        content: "",
        dirtyScenes: {},
        persistSection: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedPlotRun: null,
        selectedSceneId: "scene-1",
        setContent: vi.fn(),
        setSidebarTab: vi.fn(),
        toast: vi.fn(),
      }),
    );

    await waitFor(() => {
      expect(result.current.qualityRunsByScene["scene-1"]).toEqual({ id: "quality-1" });
    });

    expect(result.current.plotRunsByScene["scene-1"]).toEqual({ id: "plot-1" });
    expect(result.current.plotTrend).toEqual({ points: [], dimensionCounts: { CAUSALITY: 2 } });
  });

  it("persists dirty content before running plot diagnosis", async () => {
    vi.spyOn(api.v2.quality, "listRuns").mockResolvedValue([] as any);
    vi.spyOn(api.v2.plotQuality, "listRuns").mockResolvedValue([] as any);
    vi.spyOn(api.v2.plotQuality, "getTrend").mockResolvedValue({ points: [], dimensionCounts: {} } as any);
    vi.spyOn(api.v2.plotQuality, "analyzeScene").mockResolvedValue({ id: "plot-run-2", overallRiskScore: 18 } as any);
    const persistSection = vi.fn().mockResolvedValue(undefined);
    const setSidebarTab = vi.fn();
    const toast = vi.fn();

    const { result } = renderHook(() =>
      useManuscriptQuality({
        applyFetchedManuscript: vi.fn(),
        content: "<p>draft scene</p>",
        dirtyScenes: { "scene-2": true },
        persistSection,
        selectedManuscriptId: "manuscript-2",
        selectedPlotRun: null,
        selectedSceneId: "scene-2",
        setContent: vi.fn(),
        setSidebarTab,
        toast,
      }),
    );

    await act(async () => {
      await result.current.runPlotDiagnosis();
    });

    expect(persistSection).toHaveBeenCalledWith("scene-2", "<p>draft scene</p>", true);
    expect(api.v2.plotQuality.analyzeScene).toHaveBeenCalledWith("manuscript-2", "scene-2");
    expect(setSidebarTab).toHaveBeenCalledWith("plot");
    expect(result.current.plotRunsByScene["scene-2"]).toEqual({ id: "plot-run-2", overallRiskScore: 18 });
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "剧情诊断已完成" }));
  });
});
