import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { useManuscriptEditorState } from "./useManuscriptEditorState";

const makeManuscript = (sections: Record<string, string>): Manuscript => ({
  id: "manuscript-1",
  outlineId: "outline-1",
  title: "正文稿",
  version: 0,
  updatedAt: "2026-07-06T00:00:00Z",
  sections,
});

const flushAsync = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe("useManuscriptEditorState", () => {
  it("never renders the previous scene's body when switching scenes", () => {
    const manuscript = makeManuscript({ "scene-1": "<p>第一场</p>", "scene-2": "<p>第二场</p>" });
    const renders: { scene: string; html: string }[] = [];
    const { rerender } = renderHook(({ scene }) => {
      const state = useManuscriptEditorState({ replaceManuscript: vi.fn(), selectedManuscript: manuscript,
        selectedManuscriptId: manuscript.id, selectedSceneId: scene, selectedStoryId: "", toast: vi.fn() });
      renders.push({ scene, html: state.content });
      return state;
    }, { initialProps: { scene: "scene-1" } });
    rerender({ scene: "scene-2" });
    expect(renders.filter(item => item.scene === "scene-2").every(item => item.html === "<p>第二场</p>")).toBe(true);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("preserves text typed while a save is in flight", async () => {
    let finish!: (value: Manuscript) => void;
    vi.spyOn(api.manuscripts, "saveSection").mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    const manuscript = makeManuscript({ "scene-1": "old" });
    const { result } = renderHook(() => useManuscriptEditorState({ replaceManuscript: vi.fn(), selectedManuscript: manuscript, selectedManuscriptId: manuscript.id, selectedSceneId: "scene-1", selectedStoryId: "s", toast: vi.fn() }));
    expect(result.current.lastSavedAt).not.toBe("");
    act(() => result.current.updateSceneDraft("scene-1", "first"));
    let saving!: Promise<void>;
    act(() => { saving = result.current.persistSection("scene-1", "first"); });
    await flushAsync();
    act(() => result.current.updateSceneDraft("scene-1", "newer"));
    await act(async () => { finish({ ...manuscript, version: 1, sections: { "scene-1": "first" } }); await saving; });
    expect(result.current.sceneDrafts["scene-1"]).toBe("newer");
    expect(result.current.dirtyScenes["scene-1"]).toBe(true);
  });
  it("retains a conflicting draft and stops automatic conflict retries", async () => {
    const save = vi.spyOn(api.manuscripts, "saveSection").mockRejectedValue(new ApiError(409, "conflict"));
    const manuscript = makeManuscript({ "scene-1": "old" });
    const { result } = renderHook(() => useManuscriptEditorState({ replaceManuscript: vi.fn(), selectedManuscript: manuscript, selectedManuscriptId: manuscript.id, selectedSceneId: "scene-1", selectedStoryId: "s", toast: vi.fn() }));
    act(() => result.current.updateSceneDraft("scene-1", "my text"));
    await act(async () => { await result.current.persistSection("scene-1", "my text"); });
    await act(async () => { await result.current.persistSection("scene-1", "my text", true); });
    expect(save).toHaveBeenCalledTimes(1);
    expect(result.current.sceneDrafts["scene-1"]).toBe("my text");
    expect(result.current.dirtyScenes["scene-1"]).toBe(true);
  });

  it("resets dirty state and scene drafts when a fresh manuscript is applied", async () => {
    const replaceManuscript = vi.fn();
    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript,
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedStoryId: "story-1",
        toast: vi.fn(),
      }),
    );

    act(() => {
      result.current.updateSceneDraft("scene-1", "<p>local draft</p>");
    });

    act(() => {
      result.current.applyFetchedManuscript(
        makeManuscript({
          "scene-1": "<p>server text</p>",
        }),
      );
    });

    expect(replaceManuscript).toHaveBeenCalledTimes(1);
    expect(result.current.sceneDrafts).toEqual({ "scene-1": "<p>server text</p>" });
    expect(result.current.dirtyScenes).toEqual({});
  });

  it("debounces autosave and only persists the last scheduled scene content", async () => {
    vi.useFakeTimers();
    vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue(
      makeManuscript({
        "scene-1": "<p>second</p>",
      }),
    );

    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript: vi.fn(),
        selectedManuscript: makeManuscript({ "scene-1": "" }),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedStoryId: "story-1",
        toast: vi.fn(),
      }),
    );

    act(() => {
      result.current.updateSceneDraft("scene-1", "<p>first</p>");
      result.current.scheduleSave("scene-1", "<p>first</p>");
      result.current.updateSceneDraft("scene-1", "<p>second</p>");
      result.current.scheduleSave("scene-1", "<p>second</p>");
    });

    await act(async () => {
      vi.advanceTimersByTime(1200);
      await Promise.resolve();
    });

    expect(api.manuscripts.saveSection).toHaveBeenCalledTimes(1);
    expect(api.manuscripts.saveSection).toHaveBeenCalledWith("manuscript-1", "scene-1", "<p>second</p>", 0);
    expect(result.current.dirtyScenes["scene-1"]).toBe(false);
    expect(result.current.sceneDrafts["scene-1"]).toBe("<p>second</p>");
  });

  it("cancels a pending empty autosave when server generation is applied and preserves other drafts", async () => {
    vi.useFakeTimers();
    const saveSection = vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue(
      makeManuscript({ "scene-1": "<p></p>" }),
    );
    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript: vi.fn(),
        selectedManuscript: makeManuscript({ "scene-1": "", "scene-2": "" }),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedStoryId: "story-1",
        toast: vi.fn(),
      }),
    );

    act(() => {
      result.current.updateSceneDraft("scene-1", "<p></p>");
      result.current.scheduleSave("scene-1", "<p></p>");
      result.current.updateSceneDraft("scene-2", "<p>other local draft</p>");
      result.current.applyServerSection(
        makeManuscript({ "scene-1": "<p>generated text</p>", "scene-2": "" }),
        "scene-1",
      );
    });

    await act(async () => {
      vi.advanceTimersByTime(1300);
      await Promise.resolve();
    });

    expect(saveSection).not.toHaveBeenCalled();
    expect(result.current.content).toBe("<p>generated text</p>");
    expect(result.current.sceneDrafts["scene-1"]).toBe("<p>generated text</p>");
    expect(result.current.sceneDrafts["scene-2"]).toBe("<p>other local draft</p>");
    expect(result.current.dirtyScenes["scene-1"]).toBeUndefined();
    expect(result.current.dirtyScenes["scene-2"]).toBe(true);
  });

  it("hydrates the editor from the selected scene and prefers local drafts", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-hydrate",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as Awaited<ReturnType<typeof api.v2.workspace.startSession>>);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.workspace.heartbeatSession>>);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.workspace.endSession>>);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.version.createVersion>>);

    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript: vi.fn(),
        selectedManuscript: makeManuscript({
          "scene-1": "<p>ab</p>",
        }),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedStoryId: "story-1",
        autoSaveIntervalSeconds: null,
        toast: vi.fn(),
      }),
    );

    await flushAsync();

    expect(result.current.content).toBe("<p>ab</p>");
    expect(result.current.currentWordCount).toBe(2);

    act(() => {
      result.current.updateSceneDraft("scene-1", "<p>abcd</p>");
    });
    await flushAsync();

    expect(result.current.content).toBe("<p>abcd</p>");
    expect(result.current.currentWordCount).toBe(4);
  });

  it("does not autosave the editor's empty pre-hydration value over server content", async () => {
    vi.useFakeTimers();
    const saveSection = vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue(
      makeManuscript({ "scene-1": "<p>edited</p>" }),
    );
    const { result, rerender } = renderHook(
      ({ selectedManuscript }: { selectedManuscript?: Manuscript }) =>
        useManuscriptEditorState({
          replaceManuscript: vi.fn(),
          selectedManuscript,
          selectedManuscriptId: "manuscript-1",
          selectedSceneId: "scene-1",
          selectedStoryId: "story-1",
          autoSaveIntervalSeconds: null,
          toast: vi.fn(),
        }),
      { initialProps: { selectedManuscript: undefined } },
    );

    act(() => {
      result.current.handleEditorChange("<p></p>");
    });

    await act(async () => {
      vi.advanceTimersByTime(1300);
      await Promise.resolve();
    });

    expect(saveSection).not.toHaveBeenCalled();

    rerender({ selectedManuscript: makeManuscript({ "scene-1": "<p>server text</p>" }) });
    await flushAsync();
    expect(result.current.content).toBe("<p>server text</p>");

    act(() => {
      result.current.handleEditorChange("<p>edited</p>");
    });
    await act(async () => {
      vi.advanceTimersByTime(1200);
      await Promise.resolve();
    });

    expect(saveSection).toHaveBeenCalledWith("manuscript-1", "scene-1", "<p>edited</p>", 0);
  });

  it("handles editor changes, tracks session deltas, and debounces save", async () => {
    vi.useFakeTimers();
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-editor",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as Awaited<ReturnType<typeof api.v2.workspace.startSession>>);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.workspace.heartbeatSession>>);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.workspace.endSession>>);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as Awaited<ReturnType<typeof api.v2.version.createVersion>>);
    vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue(
      makeManuscript({
        "scene-1": "<p>abcd</p>",
      }),
    );

    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript: vi.fn(),
        selectedManuscript: makeManuscript({
          "scene-1": "<p>ab</p>",
        }),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedStoryId: "story-1",
        autoSaveIntervalSeconds: null,
        toast: vi.fn(),
      }),
    );

    await flushAsync();

    act(() => {
      result.current.handleEditorChange("<p>abcd</p>");
    });

    expect(result.current.content).toBe("<p>abcd</p>");
    expect(result.current.sceneDrafts["scene-1"]).toBe("<p>abcd</p>");
    expect(result.current.dirtyScenes["scene-1"]).toBe(true);
    expect(result.current.sessionNetWords).toBe(2);

    await act(async () => {
      vi.advanceTimersByTime(1200);
      await Promise.resolve();
    });

    expect(api.manuscripts.saveSection).toHaveBeenCalledWith("manuscript-1", "scene-1", "<p>abcd</p>", 0);
    expect(result.current.dirtyScenes["scene-1"]).toBe(false);
  });
});

// Audit PERF-01: these exercise identity switches, not just scene switches.
describe("manuscript and branch draft isolation", () => {
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });
  const a = { ...makeManuscript({ shared: "A" }), id: "a", version: 2 };
  const b = { ...makeManuscript({ shared: "B" }), id: "b", version: 7 };
  function mount(initial = a) {
    const replace = vi.fn();
    const hook = renderHook(({ manuscript }) => useManuscriptEditorState({ selectedManuscript: manuscript,
      selectedManuscriptId: manuscript.id, selectedSceneId: "shared", selectedStoryId: "",
      replaceManuscript: replace, toast: vi.fn() }), { initialProps: { manuscript: initial } });
    return { ...hook, replace };
  }
  it("keeps separate drafts on first render, manual save and switch back", async () => {
    const save = vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue({ ...b, version: 8 });
    const { result, rerender } = mount();
    act(() => result.current.updateSceneDraft("shared", "A edited"));
    rerender({ manuscript: b });
    expect(result.current.content).toBe("B");
    await act(() => result.current.handleManualSave());
    expect(save).toHaveBeenCalledWith("b", "shared", "B", 7);
    rerender({ manuscript: a });
    expect(result.current.content).toBe("A edited");
    expect(result.current.dirtyScenes.shared).toBe(true);
  });
  it("binds an outstanding autosave to its original manuscript", async () => {
    vi.useFakeTimers();
    const save = vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue({ ...a, version: 3, sections: { shared: "A edited" } });
    const { result, rerender, replace } = mount();
    act(() => { result.current.updateSceneDraft("shared", "A edited"); result.current.scheduleSave("shared", "A edited"); });
    rerender({ manuscript: b });
    await act(async () => { await vi.advanceTimersByTimeAsync(1300); });
    expect(save).toHaveBeenCalledWith("a", "shared", "A edited", 2);
    expect(result.current.content).toBe("B");
    expect(replace).not.toHaveBeenCalled();
  });
  it("does not apply a late save response to the newly selected manuscript", async () => {
    let resolve!: (value: Manuscript) => void;
    vi.spyOn(api.manuscripts, "saveSection").mockReturnValue(new Promise(done => { resolve = done; }));
    const { result, rerender, replace } = mount();
    act(() => result.current.updateSceneDraft("shared", "A edited"));
    let pending!: Promise<void>;
    act(() => { pending = result.current.handleManualSave(); });
    await flushAsync();
    rerender({ manuscript: b });
    await act(async () => { resolve({ ...a, version: 3, sections: { shared: "A edited" } }); await pending; });
    expect(result.current.content).toBe("B");
    expect(replace).not.toHaveBeenCalled();
    rerender({ manuscript: a });
    expect(result.current.content).toBe("A edited");
    expect(result.current.dirtyScenes.shared).toBe(false);
  });
  it("isolates same-manuscript branches and includes the expected branch in saves", async () => {
    const first = { ...a, currentBranchId: "branch-a" };
    const second = { ...a, currentBranchId: "branch-b", version: 3, sections: { shared: "branch B" } };
    const save = vi.spyOn(api.manuscripts, "saveSection").mockResolvedValue({ ...second, version: 4 });
    const { result, rerender } = mount(first);
    act(() => result.current.updateSceneDraft("shared", "branch A edited"));
    rerender({ manuscript: second });
    expect(result.current.content).toBe("branch B");
    await act(() => result.current.handleManualSave());
    expect(save).toHaveBeenCalledWith("a", "shared", "branch B", 3, "branch-b");
    rerender({ manuscript: first });
    expect(result.current.content).toBe("branch A edited");
  });
  it("a version conflict only pauses the affected manuscript", async () => {
    const save = vi.spyOn(api.manuscripts, "saveSection").mockRejectedValueOnce(new ApiError(409, "conflict"))
      .mockResolvedValueOnce({ ...b, version: 8, sections: { shared: "B edited" } });
    const { result, rerender } = mount();
    await act(() => result.current.persistSection("shared", "A edited"));
    rerender({ manuscript: b });
    await act(() => result.current.persistSection("shared", "B edited", true));
    expect(save).toHaveBeenCalledTimes(2);
    expect(result.current.content).toBe("B edited");
  });
});
