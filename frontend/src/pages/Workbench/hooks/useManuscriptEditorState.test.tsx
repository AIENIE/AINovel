import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import type { Manuscript } from "@/types";
import { useManuscriptEditorState } from "./useManuscriptEditorState";

const makeManuscript = (sections: Record<string, string>): Manuscript => ({
  id: "manuscript-1",
  outlineId: "outline-1",
  title: "正文稿",
  updatedAt: "2026-07-06T00:00:00Z",
  sections,
});

describe("useManuscriptEditorState", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it("resets dirty state and scene drafts when a fresh manuscript is applied", async () => {
    const replaceManuscript = vi.fn();
    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript,
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
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
      }) as any,
    );

    const { result } = renderHook(() =>
      useManuscriptEditorState({
        replaceManuscript: vi.fn(),
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
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
    expect(api.manuscripts.saveSection).toHaveBeenCalledWith("manuscript-1", "scene-1", "<p>second</p>");
    expect(result.current.dirtyScenes["scene-1"]).toBe(false);
    expect(result.current.sceneDrafts["scene-1"]).toBe("<p>second</p>");
  });
});
