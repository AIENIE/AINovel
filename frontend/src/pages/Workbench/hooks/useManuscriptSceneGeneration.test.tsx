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
    vi.spyOn(api.manuscripts, "startGenerateScene").mockResolvedValue({ operationId: "operation-1" });
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (_id, onProgress) => {
      const completed = completedOperation("operation-1");
      onProgress(completed);
      return completed;
    });
    vi.spyOn(api.manuscripts, "get").mockResolvedValue(saved as any);
    const loadSlopQuality = vi.fn().mockResolvedValue({ status: "ACCEPTED", maxSeverity: "LOW" });
    const loadPlotQuality = vi.fn().mockResolvedValue({ run: null, trend: null });
    const applyServerSection = vi.fn();
    const toast = vi.fn();

    const { result } = renderHook(() =>
      useManuscriptSceneGeneration({
        loadPlotQuality,
        loadSlopQuality,
        applyServerSection,
        cancelPendingSectionSave: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        toast,
      }),
    );

    await act(async () => {
      await result.current.generateScene();
    });

    expect(api.manuscripts.startGenerateScene).toHaveBeenCalledWith("manuscript-1", "scene-1", "fast");
    expect(applyServerSection).toHaveBeenCalledWith(saved, "scene-1");
    expect(loadSlopQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(loadPlotQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "已生成场景正文" }));
    expect(result.current.isGenerating).toBe(false);
  });

  it("shows a destructive toast when generation fails", async () => {
    vi.spyOn(api.manuscripts, "startGenerateScene").mockRejectedValue(new Error("network down"));
    const toast = vi.fn();

    const { result } = renderHook(() =>
      useManuscriptSceneGeneration({
        loadPlotQuality: vi.fn(),
        loadSlopQuality: vi.fn(),
        applyServerSection: vi.fn(),
        cancelPendingSectionSave: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
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

  it("forwards crafted mode when the writer selects it", async () => {
    const saved = {
      id: "manuscript-2",
      outlineId: "outline-1",
      title: "正文稿",
      updatedAt: "2026-07-14T00:00:00Z",
      sections: { "scene-1": "<p>crafted</p>" },
    };
    vi.spyOn(api.manuscripts, "startGenerateScene").mockResolvedValue({ operationId: "operation-3" });
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (_id, onProgress) => {
      const completed = completedOperation("operation-3");
      onProgress(completed);
      return completed;
    });
    vi.spyOn(api.manuscripts, "get").mockResolvedValue(saved as any);

    const { result } = renderHook(() =>
      useManuscriptSceneGeneration({
        loadPlotQuality: vi.fn().mockResolvedValue({ run: null, trend: null }),
        loadSlopQuality: vi.fn().mockResolvedValue({ status: "ACCEPTED", maxSeverity: "LOW" }),
        applyServerSection: vi.fn(),
        cancelPendingSectionSave: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        toast: vi.fn(),
      }),
    );

    act(() => {
      result.current.setGenerationMode("crafted");
    });
    await act(async () => {
      await result.current.generateScene();
    });

    expect(api.manuscripts.startGenerateScene).toHaveBeenCalledWith("manuscript-1", "scene-1", "crafted");
  });

  it("treats empty editor html as a failed generation", async () => {
    vi.spyOn(api.manuscripts, "startGenerateScene").mockResolvedValue({ operationId: "operation-4" });
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (_id, onProgress) => {
      const completed = completedOperation("operation-4");
      onProgress(completed);
      return completed;
    });
    vi.spyOn(api.manuscripts, "get").mockResolvedValue({
      id: "manuscript-2",
      sections: { "scene-1": "<p><br></p>" },
    } as any);
    const applyServerSection = vi.fn();
    const toast = vi.fn();
    const { result } = renderHook(() => useManuscriptSceneGeneration({
      applyServerSection,
      cancelPendingSectionSave: vi.fn(),
      loadPlotQuality: vi.fn(),
      loadSlopQuality: vi.fn(),
      selectedManuscriptId: "manuscript-1",
      selectedSceneId: "scene-1",
      toast,
    }));

    await act(async () => result.current.generateScene());

    expect(applyServerSection).not.toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({
      variant: "destructive",
      title: "生成失败",
    }));
  });
});

function completedOperation(id: string) {
  return {
    id, operationType: "AINOVEL_LONG_TASK", status: "SUCCEEDED",
    totalSteps: 5, completedSteps: 5, remainingSteps: 0,
    currentStepOutputTokens: 3200, outputTokensEstimated: false, attemptCount: 1,
  } as any;
}
