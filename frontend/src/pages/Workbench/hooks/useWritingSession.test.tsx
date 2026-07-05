import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { api } from "@/lib/api-client";
import { useWritingSession } from "./useWritingSession";

const flushAsync = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe("useWritingSession", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("starts a writing session and ends it with the latest counters on unmount", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-1",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as any);
    const endSession = vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as any);

    const { result, unmount } = renderHook(() =>
      useWritingSession({
        selectedStoryId: "story-1",
        selectedManuscriptId: "manuscript-1",
        selectedSceneId: "scene-1",
        selectedSceneDirty: false,
        autoSaveIntervalSeconds: null,
        measureHtmlWords: (html) => html.length,
      }),
    );

    await flushAsync();

    act(() => {
      result.current.recordSceneHtml("scene-1", "abcd");
      result.current.recordSceneHtml("scene-1", "ab");
    });

    expect(result.current.sessionWordsWritten).toBe(4);
    expect(result.current.sessionWordsDeleted).toBe(2);
    expect(result.current.sessionNetWords).toBe(2);

    unmount();

    expect(endSession).toHaveBeenCalledWith("session-1", {
      wordsWritten: 4,
      wordsDeleted: 2,
    });
  });

  it("tracks deltas relative to primed scene content", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-prime",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as any);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as any);

    const { result } = renderHook(() =>
      useWritingSession({
        selectedStoryId: "story-prime",
        selectedManuscriptId: "manuscript-prime",
        selectedSceneId: "scene-prime",
        selectedSceneDirty: false,
        autoSaveIntervalSeconds: null,
        measureHtmlWords: (html) => html.length,
      }),
    );

    await flushAsync();

    act(() => {
      result.current.primeSceneHtml("scene-prime", "abcd");
      result.current.recordSceneHtml("scene-prime", "abcdef");
      result.current.recordSceneHtml("scene-prime", "abc");
    });

    expect(result.current.sessionWordsWritten).toBe(2);
    expect(result.current.sessionWordsDeleted).toBe(3);
    expect(result.current.sessionNetWords).toBe(-1);
  });

  it("heartbeats with the latest counters once the session is active", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-2",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as any);
    const heartbeatSession = vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as any);

    const { result } = renderHook(() =>
      useWritingSession({
        selectedStoryId: "story-2",
        selectedManuscriptId: "manuscript-2",
        selectedSceneId: "scene-2",
        selectedSceneDirty: false,
        autoSaveIntervalSeconds: null,
        measureHtmlWords: (html) => html.length,
      }),
    );

    await flushAsync();

    act(() => {
      result.current.recordSceneHtml("scene-2", "abc");
    });
    await flushAsync();

    act(() => {
      vi.advanceTimersByTime(30000);
    });
    await flushAsync();

    expect(heartbeatSession).toHaveBeenCalledWith("session-2", {
      wordsWritten: 3,
      wordsDeleted: 0,
    });
  });

  it("throttles automatic snapshots by the configured interval", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-3",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as any);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as any);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as any);
    const createVersion = vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as any);

    renderHook(() =>
      useWritingSession({
        selectedStoryId: "story-3",
        selectedManuscriptId: "manuscript-3",
        selectedSceneId: "scene-3",
        selectedSceneDirty: true,
        autoSaveIntervalSeconds: 30,
        measureHtmlWords: (html) => html.length,
      }),
    );

    await flushAsync();

    expect(createVersion).toHaveBeenCalledTimes(1);
    expect(createVersion).toHaveBeenLastCalledWith("manuscript-3", expect.objectContaining({ snapshotType: "auto" }));

    act(() => {
      vi.advanceTimersByTime(29000);
    });
    await flushAsync();
    expect(createVersion).toHaveBeenCalledTimes(1);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    await flushAsync();
    expect(createVersion).toHaveBeenCalledTimes(2);
  });
});
