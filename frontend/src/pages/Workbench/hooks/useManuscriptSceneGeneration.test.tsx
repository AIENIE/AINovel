import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useManuscriptSceneGeneration } from "./useManuscriptSceneGeneration";

describe("useManuscriptSceneGeneration", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("replaces manuscript content and refreshes quality data after generation", async () => {
    const saved = {
      id: "manuscript-2",
      outlineId: "outline-1",
      title: "正文稿",
      updatedAt: "2026-07-06T00:00:00Z",
      sections: {
        "scene-1": "<p>generated</p>",
      },
    };
    vi.spyOn(api.manuscripts, "generateScene").mockResolvedValue(saved as any);
    const loadSlopQuality = vi.fn().mockResolvedValue({ status: "ACCEPTED", maxSeverity: "LOW" });
    const loadPlotQuality = vi.fn().mockResolvedValue({ run: null, trend: null });
    const replaceManuscript = vi.fn();
    const setContent = vi.fn();
    const toast = vi.fn();

    const { result } = renderHook(() =>
      useManuscriptSceneGeneration({
        loadPlotQuality,
        loadSlopQuality,
        replaceManuscript,
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        setContent,
        toast,
      }),
    );

    await act(async () => {
      await result.current.generateScene();
    });

    expect(api.manuscripts.generateScene).toHaveBeenCalledWith("manuscript-1", "scene-1");
    expect(replaceManuscript).toHaveBeenCalledWith(saved);
    expect(setContent).toHaveBeenCalledWith("<p>generated</p>");
    expect(loadSlopQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(loadPlotQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "已生成场景正文" }));
    expect(result.current.isGenerating).toBe(false);
  });

  it("shows a destructive toast when generation fails", async () => {
    vi.spyOn(api.manuscripts, "generateScene").mockRejectedValue(new Error("network down"));
    const toast = vi.fn();

    const { result } = renderHook(() =>
      useManuscriptSceneGeneration({
        loadPlotQuality: vi.fn(),
        loadSlopQuality: vi.fn(),
        replaceManuscript: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        setContent: vi.fn(),
        toast,
      }),
    );

    await act(async () => {
      await result.current.generateScene();
    });

    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({
        variant: "destructive",
        title: "生成失败",
      }),
    );
    expect(result.current.isGenerating).toBe(false);
  });
});
