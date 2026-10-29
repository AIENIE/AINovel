import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, renderHook } from "@testing-library/react";
import { api, ApiError } from "@/lib/api-client";
import * as clientErrors from "@/lib/client-error-reporting";
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

  afterEach(async () => {
    cleanup();
    await flushAsync();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("starts a writing session and ends it with the latest counters on unmount", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({
      id: "session-1",
      startedAt: "2026-07-06T00:00:00Z",
      wordsWritten: 0,
      wordsDeleted: 0,
    } as never);
    const endSession = vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as never);

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
    } as never);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as never);

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
    } as never);
    const heartbeatSession = vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as never);

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
    } as never);
    vi.spyOn(api.v2.workspace, "heartbeatSession").mockResolvedValue({} as never);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as never);
    const createVersion = vi.spyOn(api.v2.version, "createVersion").mockResolvedValue({} as never);

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

  it("retains counters after a rejected heartbeat and recovers on the next regular tick", async () => {
    vi.spyOn(api.v2.workspace, "startSession").mockResolvedValue({id:"retained-session",startedAt:new Date().toISOString(),wordsWritten:0,wordsDeleted:0} as never);
    vi.spyOn(api.v2.workspace, "endSession").mockResolvedValue({} as never);
    const failure=new ApiError(503,"SESSION_VALIDATION_UNAVAILABLE");
    const heartbeat=vi.spyOn(api.v2.workspace,"heartbeatSession").mockRejectedValueOnce(failure).mockResolvedValue({} as never);
    const report=vi.spyOn(clientErrors,"reportClientError").mockImplementation(()=>{});
    const {result,unmount}=renderHook(()=>useWritingSession({selectedStoryId:"story",selectedManuscriptId:"manuscript",selectedSceneId:"scene",selectedSceneDirty:false,measureHtmlWords:html=>html.length}));
    await flushAsync();act(()=>result.current.recordSceneHtml("scene","abcd"));await flushAsync();
    act(()=>vi.advanceTimersByTime(30000));await flushAsync();
    expect(report).toHaveBeenCalledExactlyOnceWith(failure,"writing.session-heartbeat");
    expect(result.current.sessionNetWords).toBe(4);expect(heartbeat).toHaveBeenCalledTimes(1);
    act(()=>result.current.recordSceneHtml("scene","abcde"));await flushAsync();
    act(()=>vi.advanceTimersByTime(29999));await flushAsync();expect(heartbeat).toHaveBeenCalledTimes(1);
    act(()=>vi.advanceTimersByTime(1));await flushAsync();
    expect(heartbeat).toHaveBeenLastCalledWith("retained-session",{wordsWritten:5,wordsDeleted:0});
    expect(result.current.sessionNetWords).toBe(5);expect(report).toHaveBeenCalledOnce();unmount();
  });

  it.each(["unmount","beforeunload"] as const)("handles an unavailable end request on %s without replaying it", async trigger=>{
    vi.spyOn(api.v2.workspace,"startSession").mockResolvedValue({id:"closing-session",startedAt:new Date().toISOString(),wordsWritten:0,wordsDeleted:0} as never);
    vi.spyOn(api.v2.workspace,"heartbeatSession").mockResolvedValue({} as never);
    const failure=new ApiError(503,"SESSION_VALIDATION_UNAVAILABLE");
    const end=vi.spyOn(api.v2.workspace,"endSession").mockRejectedValueOnce(failure).mockResolvedValue({} as never);
    const report=vi.spyOn(clientErrors,"reportClientError").mockImplementation(()=>{});
    const {result,unmount}=renderHook(()=>useWritingSession({selectedStoryId:"story",selectedManuscriptId:"manuscript",selectedSceneId:"scene",selectedSceneDirty:false,measureHtmlWords:html=>html.length}));
    await flushAsync();act(()=>result.current.recordSceneHtml("scene","abc"));await flushAsync();
    if(trigger==="unmount")unmount();else act(()=>window.dispatchEvent(new Event("beforeunload")));
    await flushAsync();expect(end).toHaveBeenCalledExactlyOnceWith("closing-session",{wordsWritten:3,wordsDeleted:0});
    expect(report).toHaveBeenCalledExactlyOnceWith(failure,trigger==="unmount"?"writing.session-end":"writing.session-unload");
    if(trigger==="beforeunload"){expect(result.current.sessionNetWords).toBe(3);unmount();await flushAsync();}
  });
});
