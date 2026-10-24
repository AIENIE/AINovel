import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import { useManuscriptSceneGeneration } from "./useManuscriptSceneGeneration";

vi.mock("@/lib/world-context-preview",()=>({reviewWorldPreview:vi.fn(async (_path,_request,preview)=>preview)}));
describe("useManuscriptSceneGeneration", () => {
  beforeEach(()=>{vi.spyOn(api.worldContext,"preview").mockResolvedValue({purpose:'SCENE',contextHash:'world-context',preferences:{worldId:null,revision:0,reviewedVersionId:null,choices:{},reviewedHashes:{}},selection:null,compiledContext:'',firstSources:[],availableSources:[]});});
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
    vi.spyOn(api.manuscripts, "get").mockResolvedValue(saved as never);
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

    expect(api.manuscripts.startGenerateScene).toHaveBeenCalledWith("manuscript-1", "scene-1", "fast", {contextHash:"world-context",expectedSelectionRevision:0});
    expect(applyServerSection).toHaveBeenCalledWith(saved, "scene-1");
    expect(loadSlopQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(loadPlotQuality).toHaveBeenCalledWith("scene-1", "manuscript-2");
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: "已生成场景正文" }));
    expect(result.current.isGenerating).toBe(false);
  });

  it("delivers NEEDS_REVIEW without fetching or overwriting manuscript content", async () => {
    vi.spyOn(api.manuscripts, "startGenerateScene").mockResolvedValue({ operationId: "review-op" });
    vi.spyOn(api.aiOperations, "wait").mockResolvedValue({ ...completedOperation("review-op") as object,
      resultJson: JSON.stringify({ outcome: "NEEDS_REVIEW", manuscriptId: "m", sceneId: "s", candidateIds: ["a", "b"], actualHan: 2750, minHan: 2800, maxHan: 3200, revisionCount: 1 }) } as never);
    const get = vi.spyOn(api.manuscripts, "get"), apply = vi.fn(), toast = vi.fn();
    const { result } = renderHook(() => useManuscriptSceneGeneration({ applyServerSection: apply,
      cancelPendingSectionSave: vi.fn(), loadPlotQuality: vi.fn(), loadSlopQuality: vi.fn(), selectedManuscriptId: "m", selectedSceneId: "s", toast }));
    await act(async () => result.current.generateScene());
    expect(get).not.toHaveBeenCalled(); expect(apply).not.toHaveBeenCalled();
    expect(toast).toHaveBeenCalledWith({ title: "正文已生成，篇幅待确认" });
  });

  it("does not start a new paid task over unsaved edits", async () => {
    const start = vi.spyOn(api.manuscripts, "startGenerateScene");
    const { result } = renderHook(() => useManuscriptSceneGeneration({ applyServerSection: vi.fn(), hasUnsavedChanges: true,
      cancelPendingSectionSave: vi.fn(), loadPlotQuality: vi.fn(), loadSlopQuality: vi.fn(), selectedManuscriptId: "m", selectedSceneId: "s", toast: vi.fn() }));
    await act(async () => result.current.generateScene()); expect(start).not.toHaveBeenCalled();
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
    vi.spyOn(api.manuscripts, "get").mockResolvedValue(saved as never);

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

    expect(api.manuscripts.startGenerateScene).toHaveBeenCalledWith("manuscript-1", "scene-1", "crafted", {contextHash:"world-context",expectedSelectionRevision:0});
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
    } as never);
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
  it("does not admit a task after new input during context review",async()=>{
    const preview=await api.worldContext.preview('/fixture',{});
    let release!:(value:typeof preview)=>void;
    vi.mocked(api.worldContext.preview).mockImplementation(()=>new Promise(resolve=>{release=resolve;}));
    const start=vi.spyOn(api.manuscripts,'startGenerateScene');
    const {result,rerender}=renderHook(({dirty})=>useManuscriptSceneGeneration({selectedManuscriptId:'m',selectedSceneId:'s',hasUnsavedChanges:dirty,applyServerSection:vi.fn(),cancelPendingSectionSave:vi.fn(),loadPlotQuality:vi.fn(),loadSlopQuality:vi.fn(),toast:vi.fn()}),{initialProps:{dirty:false}});
    let task!:Promise<void>;act(()=>{task=result.current.generateScene();});rerender({dirty:true});
    await act(async()=>{release(preview);await task;});expect(start).not.toHaveBeenCalled();
  });
  it("does not apply a late result to a switched scene",async()=>{
    vi.spyOn(api.manuscripts,'startGenerateScene').mockResolvedValue({operationId:'late'});
    let release!:(value:never)=>void;vi.spyOn(api.aiOperations,'wait').mockImplementation(()=>new Promise(resolve=>{release=resolve;}));
    const apply=vi.fn(),get=vi.spyOn(api.manuscripts,'get');
    const {result,rerender}=renderHook(({scene})=>useManuscriptSceneGeneration({selectedManuscriptId:'m',selectedSceneId:scene,applyServerSection:apply,cancelPendingSectionSave:vi.fn(),loadPlotQuality:vi.fn(),loadSlopQuality:vi.fn(),toast:vi.fn()}),{initialProps:{scene:'first'}});
    let task!:Promise<void>;act(()=>{task=result.current.generateScene();});await waitFor(()=>expect(api.aiOperations.wait).toHaveBeenCalled());
    rerender({scene:'second'});await act(async()=>{release(completedOperation('late'));await task;});expect(apply).not.toHaveBeenCalled();expect(get).not.toHaveBeenCalled();
  });

});

function completedOperation(id: string) {
  return {
    id, operationType: "AINOVEL_LONG_TASK", status: "SUCCEEDED",
    totalSteps: 5, completedSteps: 5, remainingSteps: 0,
    currentStepOutputTokens: 3200, outputTokensEstimated: false, attemptCount: 1,
  } as never;
}
