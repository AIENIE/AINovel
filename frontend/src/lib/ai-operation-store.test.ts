import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api-client";
import {
  AiOperationCancelledError,
  setTrackedAiUser, setTrackedAiVisibility, useTrackedAiVisibility, resumeTrackedAiOperation,
  cancelTrackedAiOperation,
  retryTrackedAiOperation,
  refreshTrackedAiOperation,
  runTrackedAiOperation,
  useTrackedAiOperation,
} from "@/lib/ai-operation-store";
import type { AiOperationProgress } from "@/types";
import { WORLD_UPDATED } from "@/lib/world-operation-events";

describe("ai operation tracking", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    act(() => setTrackedAiUser(null));
    window.sessionStorage.clear();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("restores a closed failed panel for its owner after refresh without retrying", async () => {
    setTrackedAiUser("owner-a");
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (id, progress) => {
      const failed = { ...completedOperation(id), status: "FAILED" as const };
      progress?.(failed); return failed;
    });
    const retry = vi.spyOn(api.aiOperations, "retry");
    const { result } = renderHook(() => useTrackedAiVisibility());
    await act(async () => { await expect(runTrackedAiOperation(Promise.resolve({ operationId: "failure-a" }))).rejects.toThrow(); });
    act(() => setTrackedAiVisibility("closed"));
    act(() => setTrackedAiUser("owner-b"));
    expect(result.current).toBe("open");
    await act(async () => { setTrackedAiUser("owner-a"); await resumeTrackedAiOperation(); });
    expect(result.current).toBe("closed");
    expect(retry).not.toHaveBeenCalled();
    act(() => setTrackedAiUser(null));
  });

  it("keeps waiting past the former 180 second client timeout", async () => {
    vi.useFakeTimers();
    const pending = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait").mockReturnValue(pending.promise);
    const cancel = vi.spyOn(api.aiOperations, "cancel").mockResolvedValue(cancelledOperation("operation-long"));

    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "operation-long" }));
    await vi.advanceTimersByTimeAsync(10 * 60_000);

    expect(cancel).not.toHaveBeenCalled();
    pending.resolve(completedOperation("operation-long"));
    await expect(operation).resolves.toMatchObject({ status: "SUCCEEDED" });
    await vi.advanceTimersByTimeAsync(4_000);
  });

  it("still aborts the watcher when the user explicitly cancels", async () => {
    const watcherStarted = deferred<void>();
    vi.spyOn(api.aiOperations, "wait").mockImplementation((_id, _onProgress, signal) => {
      watcherStarted.resolve(undefined);
      return new Promise((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("Operation aborted", "AbortError")));
      });
    });
    const cancel = vi.spyOn(api.aiOperations, "cancel").mockResolvedValue(cancelledOperation("operation-cancelled"));
    vi.spyOn(api.aiOperations, "get").mockResolvedValue(cancelledOperation("operation-cancelled"));

    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "operation-cancelled" }));
    await watcherStarted.promise;
    await cancelTrackedAiOperation("operation-cancelled");

    await expect(operation).rejects.toBeInstanceOf(AiOperationCancelledError);
    expect(cancel).toHaveBeenCalledWith("operation-cancelled");
  });

  it("does not report cancellation when the result committed first", async () => {
    const started = deferred<void>();
    const remote = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait").mockImplementation(() => { started.resolve(undefined); return remote.promise; });
    vi.spyOn(api.aiOperations, "cancel").mockResolvedValue(completedOperation("operation-finished"));
    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "operation-finished" }));
    await started.promise;
    await cancelTrackedAiOperation("operation-finished");
    remote.resolve(completedOperation("operation-finished"));
    await expect(operation).resolves.toMatchObject({ status: "SUCCEEDED" });
  });
  it.each([
    ["WRITING_ASSISTANT_TURN", "ainovel:writing-assistant-updated"],
    ["STORY_ASSISTANT_TURN", "ainovel:story-assistant-updated"],
    ["WRITING_AGENT", "ainovel:writing-agent-updated"],
  ])("refreshes %s in place after recovery without reloading unsaved editor text", async (operationType, eventType) => {
    vi.useFakeTimers();
    const reload = vi.fn(), assign = vi.fn(), dispatch = vi.fn();
    const originalWindow = window;
    vi.stubGlobal("window", { sessionStorage: originalWindow.sessionStorage, setTimeout: originalWindow.setTimeout.bind(originalWindow), dispatchEvent: dispatch, location: { reload, assign } });
    try {
      vi.spyOn(api.aiOperations, "retry").mockResolvedValue({ operationId: "assistant-recovered" } as Awaited<ReturnType<typeof api.aiOperations.retry>>);
      vi.spyOn(api.aiOperations, "wait").mockResolvedValue({ ...completedOperation("assistant-recovered"), operationType });
      await retryTrackedAiOperation("assistant-recovered");
      expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: eventType }));
      expect(reload).not.toHaveBeenCalled();
      expect(assign).not.toHaveBeenCalled();
      await vi.advanceTimersByTimeAsync(4000);
    } finally { vi.unstubAllGlobals(); }
  });

  it("recovers a persisted writing-agent operation without retrying or reloading the editor", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    setTrackedAiUser("agent-owner");
    window.sessionStorage.setItem("ainovel.active-ai-operation:agent-owner", "agent-resumed");
    const wait = vi.spyOn(api.aiOperations, "wait").mockResolvedValue({ ...completedOperation("agent-resumed"), operationType: "WRITING_AGENT" });
    const retry = vi.spyOn(api.aiOperations, "retry");

    await resumeTrackedAiOperation();

    expect(wait).toHaveBeenCalledExactlyOnceWith("agent-resumed", expect.any(Function), expect.any(AbortSignal));
    expect(browser.dispatch).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ type: "ainovel:writing-agent-updated" }));
    expect(retry).not.toHaveBeenCalled();
    expect(browser.reload).not.toHaveBeenCalled();
    expect(browser.assign).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(4000);
  });

  it("notifies the current world editor when a normally tracked background operation finishes", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    const remote = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait").mockReturnValue(remote.promise);
    const retry = vi.spyOn(api.aiOperations, "retry");

    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "world-background" }));
    await Promise.resolve();
    remote.resolve(worldOperation("world-background", "world-a"));
    await operation;

    expect(browser.dispatch).toHaveBeenCalledTimes(1);
    expect(browser.dispatch).toHaveBeenCalledWith(expect.objectContaining({
      type: WORLD_UPDATED,
      detail: { worldId: "world-a", operationId: "world-background" },
    }));
    expect(retry).not.toHaveBeenCalled();
    expect(browser.reload).not.toHaveBeenCalled();
    expect(browser.assign).not.toHaveBeenCalled();
  });

  it("recovers the original world operation with a single update and no new request", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    setTrackedAiUser("world-owner");
    window.sessionStorage.setItem("ainovel.active-ai-operation:world-owner", "world-resumed");
    const wait = vi.spyOn(api.aiOperations, "wait").mockResolvedValue(worldOperation("world-resumed", "world-a"));
    const retry = vi.spyOn(api.aiOperations, "retry");

    await resumeTrackedAiOperation();

    expect(wait).toHaveBeenCalledWith("world-resumed", expect.any(Function), expect.any(AbortSignal));
    expect(browser.dispatch).toHaveBeenCalledTimes(1);
    expect(browser.dispatch).toHaveBeenCalledWith(expect.objectContaining({
      type: WORLD_UPDATED,
      detail: { worldId: "world-a", operationId: "world-resumed" },
    }));
    expect(retry).not.toHaveBeenCalled();
    expect(browser.reload).not.toHaveBeenCalled();
    expect(browser.assign).not.toHaveBeenCalled();
  });

  it.each([
    { scopeId: "world-a", resultJson: '{"id":"world-b"}', expectedWorldId: "world-a" },
    { scopeId: null, resultJson: '{"id":"world-b"}', expectedWorldId: "world-b" },
    { scopeId: "world-a", resultJson: "invalid-json", expectedWorldId: "world-a" },
    { scopeId: null, resultJson: "invalid-json", expectedWorldId: null },
    { scopeId: null, resultJson: '{"id":42}', expectedWorldId: null },
  ])("recovers a world result without navigation: $scopeId / $resultJson", async ({ scopeId, resultJson, expectedWorldId }) => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    const retry = vi.spyOn(api.aiOperations, "retry").mockResolvedValue({ operationId: "world-retried" });
    const wait = vi.spyOn(api.aiOperations, "wait").mockResolvedValue({
      ...worldOperation("world-retried", scopeId), resultJson,
    });

    await retryTrackedAiOperation("world-retried");

    expect(retry).toHaveBeenCalledExactlyOnceWith("world-retried");
    expect(wait).toHaveBeenCalledExactlyOnceWith("world-retried", expect.any(Function), expect.any(AbortSignal));
    if (expectedWorldId) {
      expect(browser.dispatch).toHaveBeenCalledTimes(1);
      expect(browser.dispatch).toHaveBeenCalledWith(expect.objectContaining({
        type: WORLD_UPDATED,
        detail: { worldId: expectedWorldId, operationId: "world-retried" },
      }));
    } else {
      expect(browser.dispatch).not.toHaveBeenCalled();
    }
    expect(browser.reload).not.toHaveBeenCalled();
    expect(browser.assign).not.toHaveBeenCalled();
  });

  it.each(["FAILED", "RECOVERY_REQUIRED"] as const)("preserves a recovered world operation in %s without retrying or refreshing", async (status) => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    setTrackedAiUser("world-owner");
    window.sessionStorage.setItem("ainovel.active-ai-operation:world-owner", "world-failed");
    const failed = { ...worldOperation("world-failed", "world-a"), status, errorMessage: "world generation failed" };
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (_id, progress) => {
      progress(failed);
      return failed;
    });
    const retry = vi.spyOn(api.aiOperations, "retry");
    const { result } = renderHook(() => useTrackedAiOperation());

    await act(async () => { await resumeTrackedAiOperation(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });

    expect(result.current).toEqual(failed);
    expect(window.sessionStorage.getItem("ainovel.active-ai-operation:world-owner")).toBe("world-failed");
    expect(retry).not.toHaveBeenCalled();
    expect(browser.dispatch).not.toHaveBeenCalled();
    expect(browser.reload).not.toHaveBeenCalled();
    expect(browser.assign).not.toHaveBeenCalled();
  });

  it("ignores late world progress and success after changing the signed-in user", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    setTrackedAiUser("original-owner");
    const remote = deferred<AiOperationProgress>();
    let progress!: (progress: AiOperationProgress) => void;
    vi.spyOn(api.aiOperations, "wait").mockImplementation((_id, onProgress) => {
      progress = onProgress;
      return remote.promise;
    });
    const { result } = renderHook(() => useTrackedAiOperation());
    const operation = runTrackedAiOperation(Promise.resolve({ operationId: "old-owner-operation" }));
    await Promise.resolve();

    act(() => setTrackedAiUser("new-owner"));
    await act(async () => {
      const final = worldOperation("old-owner-operation", "world-a");
      progress(final);
      remote.resolve(final);
      await operation;
    });

    expect(result.current).toBeNull();
    expect(browser.dispatch).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem("ainovel.active-ai-operation:new-owner")).toBeNull();
  });

  it("ignores a replaced watcher while notifying the current world task", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    const earlier = deferred<AiOperationProgress>();
    const latest = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait").mockReturnValueOnce(earlier.promise).mockReturnValueOnce(latest.promise);
    const first = runTrackedAiOperation(Promise.resolve({ operationId: "world-first" }));
    await Promise.resolve();
    const second = runTrackedAiOperation(Promise.resolve({ operationId: "world-second" }));
    await Promise.resolve();

    earlier.resolve(worldOperation("world-first", "world-a"));
    await first;
    expect(browser.dispatch).not.toHaveBeenCalled();
    latest.resolve(worldOperation("world-second", "world-b"));
    await second;

    expect(browser.dispatch).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      type: WORLD_UPDATED,
      detail: { worldId: "world-b", operationId: "world-second" },
    }));
  });

  it("does not clear a new watcher for the same operation when an old success timer expires", async () => {
    vi.useFakeTimers();
    stubBrowser();
    setTrackedAiUser("world-owner");
    const remote = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "wait")
      .mockResolvedValueOnce(worldOperation("world-same-id", "world-a"))
      .mockReturnValueOnce(remote.promise);
    vi.spyOn(api.aiOperations, "retry").mockResolvedValue({ operationId: "world-same-id" });
    await runTrackedAiOperation(Promise.resolve({ operationId: "world-same-id" }));
    await vi.advanceTimersByTimeAsync(2000);

    const retry = retryTrackedAiOperation("world-same-id");
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(3000);

    expect(window.sessionStorage.getItem("ainovel.active-ai-operation:world-owner")).toBe("world-same-id");
    remote.resolve(worldOperation("world-same-id", "world-a"));
    await retry;
  });

  it("reads a disconnected world task to completion without retrying or reloading", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    const { result } = renderHook(() => useTrackedAiOperation());
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (id, onProgress) => {
      onProgress({ ...worldOperation(id, "world-a"), status: "RUNNING" });
      throw new Error("offline");
    });
    const get = vi.spyOn(api.aiOperations, "get").mockResolvedValue(worldOperation("disconnected", "world-a"));
    const retry = vi.spyOn(api.aiOperations, "retry");
    await act(async () => { await expect(runTrackedAiOperation(Promise.resolve({ operationId: "disconnected" }))).rejects.toThrow("offline"); });
    expect(result.current?.status).toBe("RUNNING");

    await act(async () => { await refreshTrackedAiOperation("disconnected"); });

    expect(get).toHaveBeenCalledExactlyOnceWith("disconnected");
    expect(result.current?.status).toBe("SUCCEEDED");
    expect(browser.dispatch).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ type: WORLD_UPDATED, detail: { worldId: "world-a", operationId: "disconnected" } }));
    expect(retry).not.toHaveBeenCalled();
    expect(browser.reload).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
    expect(result.current).toBeNull();
  });

  it.each(["FAILED", "RECOVERY_REQUIRED"] as const)("keeps a refreshed %s world task recoverable without emitting a success event", async status => {
    const browser = stubBrowser();
    const { result } = renderHook(() => useTrackedAiOperation());
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (id, onProgress) => {
      onProgress({ ...worldOperation(id, "world-a"), status: "RUNNING" });
      throw new Error("offline");
    });
    vi.spyOn(api.aiOperations, "get").mockResolvedValue({ ...worldOperation("disconnected", "world-a"), status, errorCode: "WORLD_VERSION_CONFLICT" });
    const retry = vi.spyOn(api.aiOperations, "retry");
    await act(async () => { await expect(runTrackedAiOperation(Promise.resolve({ operationId: "disconnected" }))).rejects.toThrow("offline"); });

    await act(async () => { await refreshTrackedAiOperation("disconnected"); });

    expect(result.current).toMatchObject({ id: "disconnected", status, errorCode: "WORLD_VERSION_CONFLICT" });
    expect(window.sessionStorage.getItem("ainovel.active-ai-operation:")).toBe("disconnected");
    expect(browser.dispatch).not.toHaveBeenCalled();
    expect(retry).not.toHaveBeenCalled();
  });

  it("does not let an older stream regress a terminal read or send a duplicate world event", async () => {
    vi.useFakeTimers();
    const browser = stubBrowser();
    const { result } = renderHook(() => useTrackedAiOperation());
    const remote = deferred<AiOperationProgress>();
    let progress!: (value: AiOperationProgress) => void;
    vi.spyOn(api.aiOperations, "wait").mockImplementation((_id, onProgress) => { progress = onProgress; return remote.promise; });
    vi.spyOn(api.aiOperations, "get").mockResolvedValue(worldOperation("finishing", "world-a"));
    let running!: Promise<AiOperationProgress>;
    await act(async () => { running = runTrackedAiOperation(Promise.resolve({ operationId: "finishing" })); });
    await act(async () => { await refreshTrackedAiOperation("finishing"); });

    act(() => progress({ ...worldOperation("finishing", "world-a"), status: "RUNNING" }));
    await act(async () => { remote.resolve(worldOperation("finishing", "world-a")); await running; });

    expect(result.current?.status).toBe("SUCCEEDED");
    expect(browser.dispatch).toHaveBeenCalledTimes(1);
  });

  it.each(["user", "watch"] as const)("ignores a manual refresh after the %s is replaced", async replacement => {
    const browser = stubBrowser();
    setTrackedAiUser("owner-a");
    const { result } = renderHook(() => useTrackedAiOperation());
    vi.spyOn(api.aiOperations, "wait").mockImplementation(async (id, onProgress) => {
      const failed = { ...worldOperation(id, "world-a"), status: "FAILED" as const };
      onProgress(failed); return failed;
    });
    const response = deferred<AiOperationProgress>();
    vi.spyOn(api.aiOperations, "get").mockReturnValue(response.promise);
    await act(async () => { await expect(runTrackedAiOperation(Promise.resolve({ operationId: "same-id" }))).rejects.toThrow(); });
    const refreshing = refreshTrackedAiOperation("same-id");
    await act(async () => {
      if (replacement === "user") setTrackedAiUser("owner-b");
      else await expect(runTrackedAiOperation(Promise.resolve({ operationId: "same-id" }))).rejects.toThrow();
      response.resolve(worldOperation("same-id", "world-a")); await refreshing;
    });

    expect(result.current?.status ?? null).toBe(replacement === "user" ? null : "FAILED");
    expect(browser.dispatch).not.toHaveBeenCalled();
  });

  it("does not attach an old retry to a different user's session", async () => {
    const browser = stubBrowser();
    setTrackedAiUser("original-owner");
    const response = deferred<{ operationId: string }>();
    vi.spyOn(api.aiOperations, "retry").mockReturnValue(response.promise);
    const wait = vi.spyOn(api.aiOperations, "wait");

    const retry = retryTrackedAiOperation("old-owner-operation");
    setTrackedAiUser("new-owner");
    response.resolve({ operationId: "old-owner-operation" });
    await retry;

    expect(wait).not.toHaveBeenCalled();
    expect(browser.dispatch).not.toHaveBeenCalled();
    expect(window.sessionStorage.getItem("ainovel.active-ai-operation:new-owner")).toBeNull();
  });
});

function stubBrowser() {
  const reload = vi.fn(), assign = vi.fn(), dispatch = vi.fn();
  const originalWindow = window;
  vi.stubGlobal("window", {
    sessionStorage: originalWindow.sessionStorage,
    HTMLIFrameElement: originalWindow.HTMLIFrameElement,
    setTimeout: originalWindow.setTimeout.bind(originalWindow),
    dispatchEvent: dispatch,
    location: { reload, assign },
  });
  return { reload, assign, dispatch };
}

function worldOperation(id: string, worldId: string | null): AiOperationProgress {
  return { ...completedOperation(id), scopeType: "WORLD", scopeId: worldId };
}

function completedOperation(id: string): AiOperationProgress {
  return {
    id,
    operationType: "AINOVEL_LONG_TASK",
    status: "SUCCEEDED",
    totalSteps: 5,
    completedSteps: 5,
    remainingSteps: 0,
    currentStepOutputTokens: 3200,
    outputTokensEstimated: false,
    attemptCount: 1,
  } as AiOperationProgress;
}

function cancelledOperation(id: string): AiOperationProgress {
  return {
    ...completedOperation(id),
    status: "CANCELLED",
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}
